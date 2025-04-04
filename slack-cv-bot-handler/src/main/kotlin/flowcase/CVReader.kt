package no.jpro.slack.cv.flowcase

class CVReader {
    private val flowcaseService: FlowcaseService

    init {
        val api_key= System.getenv("FLOWCASE_API_KEY")
        if(api_key.isNullOrBlank()) {throw IllegalArgumentException("missing environent property FLOWCASE_API_KEY")}
        flowcaseService = FlowcaseService.connect(api_key)
    }

    fun readCV(email: String): FlowcaseService.FlowcaseCv {
        val user = flowcaseService.user(email)
        return flowcaseService.cv(user.id, user.default_cv_id)
    }
}
