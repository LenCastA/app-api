package io.octatec.horext.api.dto

data class OrganizationUnitSummary(
    val id: Long,
    val code: String,
    val name: String,
    val parentId: Long? = null,
)

data class CourseAffiliation(
    val courseId: String,
    val faculties: List<OrganizationUnitSummary>,
    val specialities: List<OrganizationUnitSummary>,
)
