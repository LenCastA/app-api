package io.octatec.horext.api.repository

import io.octatec.horext.api.domain.HourlyLoad

interface HourlyLoadRepository {
    fun getAllByFaculty(facultyId: Long): List<HourlyLoad>

    fun getLatestByFaculty(facultyId: Long): HourlyLoad?
}
