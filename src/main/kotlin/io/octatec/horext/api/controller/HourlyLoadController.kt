package io.octatec.horext.api.controller

import io.octatec.horext.api.domain.HourlyLoad
import io.octatec.horext.api.service.HourlyLoadService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("hourlyLoads")
class HourlyLoadController(
    val hourlyLoadService: HourlyLoadService,
) {
    @GetMapping(params = ["faculty"])
    fun getAllByFaculty(
        @RequestParam(name = "faculty") facultyId: Long,
    ): List<HourlyLoad> = hourlyLoadService.getAllByFaculty(facultyId)

    @GetMapping("/latest")
    fun getLatestBySpeciality(
        @RequestParam(name = "faculty") facultyId: Long,
    ): HourlyLoad = hourlyLoadService.getLatestByFaculty(facultyId)
}
