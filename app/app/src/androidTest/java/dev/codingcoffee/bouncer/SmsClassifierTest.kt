package dev.codingcoffee.bouncer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.codingcoffee.bouncer.data.classify.SmsClassifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsClassifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val classifier = instrumentation.targetContext.assets.open(BuildConfig.SMS_MODEL_ASSET).use(SmsClassifier::load)
    private val fixtures = JSONObject(instrumentation.context.assets.open("fixtures.json").bufferedReader().readText())

    @Test
    fun matchesFixtures() {
        val tolerance = fixtures.getDouble("tolerance")
        val labels = fixtures.getJSONArray("labels")
        assertEquals(List(labels.length()) { labels.getString(it) }, classifier.labels)
        val cases = fixtures.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val text = case.getString("text")
            assertEquals(text, case.getString("template"), SmsClassifier.template(text))
            val expected = case.getJSONArray("scores")
            val actual = classifier.scores(text)
            for (k in actual.indices) assertEquals("$text [$k]", expected.getDouble(k), actual[k], tolerance)
        }
    }
}
