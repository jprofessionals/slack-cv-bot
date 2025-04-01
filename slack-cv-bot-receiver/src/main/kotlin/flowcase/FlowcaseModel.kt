package no.jpro.slack.cv.flowcase

import java.time.LocalDate


/** Aka Resume i flowcase */
data class CV(
    val name: String,
    val projects: List<Project>,
//    val work: List<Map<String, Any>> = mutableListOf(),
    val summary: String,
    val education: List<Education>,
)

data class Project(
    val customer: String,
    val name: String,
    val description: String,
    val roles: List<ProjectRole>,
    val skills: List<String>,
    val startYear: String?,
    val startMonth: String?,
    val endYear: String?,
    val endMonth: String?
){
    val startDate: LocalDate
        get()= LocalDate.of(startYear?.toInt()?:LocalDate.now().year, startMonth?.toInt()?:LocalDate.now().monthValue , 1)
    val endDate: LocalDate
        get()= LocalDate.of(endYear?.toInt()?:LocalDate.now().year, endMonth?.toInt()?:LocalDate.now().monthValue , 1)

}

data class ProjectRole (
    val name: String,
    val description: String
)

data class Education(
    val school: String,
    val degree: String,
    val description: String?,
    val start_year: String,
    val end_year: String?
)
