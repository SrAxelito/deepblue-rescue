package com.deepblue.rescue.controller;

import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.GlobalExceptionHandler;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.service.TreatmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TreatmentController.class)
@Import(GlobalExceptionHandler.class)
class TreatmentControllerTest {

    private static final String VALID_REQUEST = """
            {
              "animalCode": "AN-001",
              "specialistCode": "SPEC-001",
              "performedAt": "2026-08-21T09:00:00",
              "type": "WOUND_CARE",
              "description": "Cleaning and treatment of flipper injury."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TreatmentService service;

    // 8. POST Treatment valido -> 201
    @Test
    void shouldCreateTreatment() throws Exception {
        TreatmentResponse response = new TreatmentResponse(
                100L,
                "AN-001",
                "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning and treatment of flipper injury."
        );

        when(service.register(any(CreateTreatmentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.animalCode").value("AN-001"))
                .andExpect(jsonPath("$.specialistCode").value("SPEC-001"))
                .andExpect(jsonPath("$.type").value("WOUND_CARE"))
                .andExpect(jsonPath("$.description")
                        .value("Cleaning and treatment of flipper injury."));

        verify(service).register(any(CreateTreatmentRequest.class));
    }

    // 9. POST Treatment invalido -> 400 + details
    @Test
    void shouldReturn400WhenTreatmentRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalCode": "",
                                  "specialistCode": "",
                                  "type": null,
                                  "description": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.animalCode")
                        .value("Animal code is required"))
                .andExpect(jsonPath("$.details.specialistCode")
                        .value("Specialist code is required"))
                .andExpect(jsonPath("$.details.performedAt")
                        .value("Treatment date is required"))
                .andExpect(jsonPath("$.details.type")
                        .value("Treatment type is required"))
                .andExpect(jsonPath("$.details.description").exists());

        verify(service, never()).register(any());
    }

    // 10. POST animal inexistente -> 404
    @Test
    void shouldReturn404WhenAnimalDoesNotExist() throws Exception {
        when(service.register(any(CreateTreatmentRequest.class)))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"
                ));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isMap());
    }

    // 11. POST regla de negocio -> 409
    @Test
    void shouldReturn409WhenBusinessRuleIsViolated() throws Exception {
        when(service.register(any(CreateTreatmentRequest.class)))
                .thenThrow(new BusinessRuleException(
                        "Released animals cannot receive treatments"
                ));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Released animals cannot receive treatments"))
                .andExpect(jsonPath("$.details").isMap());
    }
}
