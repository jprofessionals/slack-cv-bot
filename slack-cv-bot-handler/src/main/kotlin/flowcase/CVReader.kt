package no.jpro.slack.cv.flowcase

class CVReader {
    private var client: FlowcaseClient

    init {
        val api_key= System.getenv("FLOWCASE_API_KEY")
        if(api_key.isNullOrBlank()) {throw IllegalArgumentException("missing environent property FLOWCASE_API_KEY")}
        client=FlowcaseClient(api_key)
    }

    fun readCV(email: String): CV {
        val cvString = client.getCV(email)
        return CVParser(cvString).parseCV()
    }

}