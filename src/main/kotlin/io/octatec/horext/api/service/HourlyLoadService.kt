package io.octatec.horext.api.service

import io.octatec.horext.api.domain.HourlyLoad

interface HourlyLoadService {
    fun getAllByFaculty(facultyId: Long): List<HourlyLoad>

    fun getLatestByFaculty(facultyId: Long): HourlyLoad
}
