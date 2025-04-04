package flowcase

import no.jpro.slack.cv.flowcase.FlowcaseService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Disabled
class FlowcaseClientIT {


    val client = FlowcaseService.connect(token = "Token mot CVPartner")

    @Test
    fun `getCV`() {
        val user = client.user("hp@jpro.no")
        val cv = client.cv(user.id, user.default_cv_id)

        assertTrue(cv.name.isNotEmpty())
        assertTrue(cv.project_experiences.isNotEmpty())
        assertTrue(cv.project_experiences[0]._id.length >= 24)
        assertTrue(cv.key_qualifications.isNotEmpty())
        assertTrue(cv.key_qualifications[0]._id.length >= 24)
    }
}
