package com.deepblue.rescue.controller;

import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.GlobalExceptionHandler;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.service.AnimalService;
import com.deepblue.rescue.service.TreatmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnimalController.class)
@Import(GlobalExceptionHandler.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnimalService animalService;

    @MockitoBean
    private TreatmentService treatmentService;

    private AnimalResponse buildAnimal(String animalCode, String caseCode) {
        return new AnimalResponse(
                1L,
                animalCode,
                "Green Sea Turtle",
                "Chelonia mydas",
                AnimalSex.FEMALE,
                caseCode,
                RescueStatus.IN_REHABILITATION
        );
    }

    // 12. GET Animal -> 200
    @Test
    void shouldReturnAnimalByCode() throws Exception {
        when(animalService.findByCode("AN-001"))
                .thenReturn(buildAnimal("AN-001", "RES-001"));

        mockMvc.perform(get("/api/animals/{code}", "AN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animalCode").value("AN-001"))
                .andExpect(jsonPath("$.commonName").value("Green Sea Turtle"))
                .andExpect(jsonPath("$.caseCode").value("RES-001"))
                .andExpect(jsonPath("$.rescueStatus").value("IN_REHABILITATION"));

        verify(animalService).findByCode("AN-001");
    }

    // 16. GET Animal inexistente -> 404 + ErrorResponse
    @Test
    void shouldReturn404WhenAnimalDoesNotExist() throws Exception {
        when(animalService.findByCode("AN-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"
                ));

        mockMvc.perform(get("/api/animals/{code}", "AN-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isMap());
    }

    // 13. GET animales en rehabilitacion -> 200
    @Test
    void shouldReturnAnimalsInRehabilitation() throws Exception {
        AnimalResponse animal1 = buildAnimal("AN-001", "RES-001");
        AnimalResponse animal2 = buildAnimal("AN-002", "RES-002");

        when(animalService.findAnimalsInRehabilitation())
                .thenReturn(List.of(animal1, animal2));

        mockMvc.perform(get("/api/animals/in-rehabilitation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].animalCode").value("AN-001"))
                .andExpect(jsonPath("$[1].animalCode").value("AN-002"));

        verify(animalService).findAnimalsInRehabilitation();
    }

    // 14. GET tratamientos del animal -> 200
    @Test
    void shouldReturnAnimalTreatments() throws Exception {
        TreatmentResponse treatment1 = new TreatmentResponse(
                100L,
                "AN-001",
                "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning and treatment of flipper injury."
        );
        TreatmentResponse treatment2 = new TreatmentResponse(
                101L,
                "AN-001",
                "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 11, 0),
                TreatmentType.HYDRATION,
                "Subcutaneous fluid therapy."
        );

        when(treatmentService.findByAnimalCode("AN-001"))
                .thenReturn(List.of(treatment1, treatment2));

        mockMvc.perform(get("/api/animals/{code}/treatments", "AN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].animalCode").value("AN-001"))
                .andExpect(jsonPath("$[0].type").value("WOUND_CARE"))
                .andExpect(jsonPath("$[1].type").value("HYDRATION"));

        verify(treatmentService).findByAnimalCode("AN-001");
    }

    // 15. GET eligibility -> 200
    @Test
    void shouldReturnTreatmentEligibility() throws Exception {
        when(animalService.canReceiveTreatment("AN-001"))
                .thenReturn(true);

        mockMvc.perform(get("/api/animals/{code}/treatment-eligibility", "AN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animalCode").value("AN-001"))
                .andExpect(jsonPath("$.eligible").value(true));

        verify(animalService).canReceiveTreatment("AN-001");
    }

    // Reto del estudiante: eligibility de un animal inexistente -> 404
    @Test
    void shouldReturn404WhenCheckingEligibilityOfMissingAnimal() throws Exception {
        when(animalService.canReceiveTreatment("AN-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"
                ));

        mockMvc.perform(get("/api/animals/{code}/treatment-eligibility", "AN-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isMap());

        verify(animalService).canReceiveTreatment("AN-999");
    }
}
