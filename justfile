gradle_file := "app/app/build.gradle.kts"
apk := "app/app/build/outputs/apk/release/app-release.apk"

default:
    @just --list

# Bump the version, build a signed APK, tag, push and publish a GitHub release
release bump:
    #!/usr/bin/env bash
    set -euo pipefail
    : "${GITHUB_TOKEN:?GITHUB_TOKEN is not set}"
    [[ "{{ bump }}" =~ ^(patch|minor|major)$ ]] || { echo "usage: just release patch|minor|major" >&2; exit 1; }
    [[ "$(git branch --show-current)" == main ]] || { echo "not on main" >&2; exit 1; }
    [[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean" >&2; exit 1; }
    git fetch --quiet origin main
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "main is not in sync with origin/main" >&2; exit 1; }

    repo=$(git remote get-url origin | sed -E 's#^(git@github\.com:|https://github\.com/)##; s#\.git$##')
    api() { curl -fsSL -H @<(printf 'Authorization: Bearer %s' "$GITHUB_TOKEN") -H "X-GitHub-Api-Version: 2022-11-28" "$@"; }

    IFS=. read -r major minor patch < <(sed -nE 's/^ *versionName = "(.*)"/\1/p' {{ gradle_file }})
    code=$(sed -nE 's/^ *versionCode = ([0-9]+)/\1/p' {{ gradle_file }})
    case "{{ bump }}" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
    esac
    version="$major.$minor.$patch"
    tag="v$version"
    code=$((code + 1))
    git rev-parse -q --verify "refs/tags/$tag" >/dev/null && { echo "tag $tag already exists" >&2; exit 1; }

    trap 'git checkout -- {{ gradle_file }}' ERR
    sed -i -E "s/^( *versionName = )\".*\"/\1\"$version\"/; s/^( *versionCode = )[0-9]+/\1$code/" {{ gradle_file }}
    rm -f {{ apk }}
    (cd app && ./gradlew assembleRelease)
    trap - ERR
    if [[ ! -f {{ apk }} ]]; then
        echo "signed APK not found; check the CLEANSMS_* Gradle properties" >&2
        git checkout -- {{ gradle_file }}
        exit 1
    fi

    git commit --quiet -m "chore: release $tag" {{ gradle_file }}
    git tag -a "$tag" -m "$tag"
    git push --atomic origin main "$tag"

    upload_url=$(jq -n --arg tag "$tag" '{tag_name: $tag, name: $tag, generate_release_notes: true}' \
        | api -X POST -H "Accept: application/vnd.github+json" --data @- "https://api.github.com/repos/$repo/releases" \
        | jq -r .upload_url)
    api -X POST -H "Content-Type: application/vnd.android.package-archive" --data-binary @{{ apk }} \
        "${upload_url%\{*}?name=cleansms-$tag.apk" >/dev/null
    echo "released $tag (versionCode $code): https://github.com/$repo/releases/tag/$tag"
