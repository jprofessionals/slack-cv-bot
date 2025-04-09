package no.jpro.slack.cv.flowcase

import feign.*
import feign.gson.GsonDecoder


interface FlowcaseService {
    @RequestLine("GET /api/v3/cvs/{userId}/{userDefaultCvId}")
    fun cv(@Param("userId") userId: String, @Param("userDefaultCvId") userDefaultCvId: String): FlowcaseCv

    @RequestLine("GET /api/v1/users/find?email={email}")
    fun user(@Param("email") email: String): FlowcaseUser

    data class FlowcaseUser(
        var id: String,
        var default_cv_id: String,
        var email: String
    )

    data class FlowcaseCv(
        val name: String,
        val project_experiences: List<ProjectExperiences>,
        val key_qualifications: List<KeyQualifications>,
    )

    data class KeyQualifications(
        val _id: String,
        val disabled: Boolean,
        val tag_line: TranslatedValue,
        val long_description: TranslatedValue,
    )

    data class ProjectExperiences(
        val _id: String,
        val disabled: Boolean,
        val customer: TranslatedValue?,
        val description: TranslatedValue?,
        val long_description: TranslatedValue?,
        val month_from: String,
        val month_to: String,
        val year_from: String,
        val year_to: String,
        val roles: List<ProjectRole>,
        val project_experience_skills: List<ProjectExperienceSkills>
    )

    data class TranslatedValue(
        val no: String?,
        val int: String?,
    )

    data class ProjectRole(
        val disabled: String,
        val name: TranslatedValue,
        val long_description: TranslatedValue,
    )

    data class ProjectExperienceSkills(
        val tags: TranslatedValue,
    )

    companion object {
        fun connect(token: String, baseUrl: String? = "https://jpro.flowcase.com"): FlowcaseService {
            return Feign.builder()
                .decoder(GsonDecoder())
                .requestInterceptor { template: RequestTemplate ->
                    template.header(
                        "Authorization", "Bearer $token"
                    )
                }
                .target(FlowcaseService::class.java, baseUrl)
        }
    }
}
