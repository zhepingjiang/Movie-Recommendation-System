package com.movierec.backend.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.AuthCookieFactory;
import com.movierec.backend.security.CustomUserDetailsService;
import com.movierec.backend.security.JwtAuthenticationFilter;
import com.movierec.backend.security.JwtService;
import com.movierec.backend.security.SecurityConfig;
import com.movierec.backend.service.NearbyTheaterService;
import com.movierec.backend.service.NearbyTheaterSort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link TheaterController}: public access and parameter validation.
 */
@WebMvcTest(controllers = TheaterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CustomUserDetailsService.class, AuthCookieFactory.class})
class TheaterControllerTest {

    @TestConfiguration
    static class SecurityMockMvcTestConfig {
        @Bean
        MockMvcBuilderCustomizer securityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NearbyTheaterService nearbyTheaterService;
    @MockitoBean private UserRepository userRepository;

    @Test
    void anonymousCallerCanSearch() throws Exception {
        when(nearbyTheaterService.findNearbyTheaters(43.6, -79.4, 5, 1L, NearbyTheaterSort.NEXT_SHOWTIME, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/theatres/nearby")
                        .param("lat", "43.6").param("lng", "-79.4").param("radiusKm", "5")
                        .param("movieId", "1").param("sort", "nextShowtime"))
                .andExpect(status().isOk());

        verify(nearbyTheaterService)
                .findNearbyTheaters(43.6, -79.4, 5, 1L, NearbyTheaterSort.NEXT_SHOWTIME, 0, 20);
    }

    @ParameterizedTest
    @CsvSource({
        "90.1, -79.4, 5, distance",
        "-90.1, -79.4, 5, distance",
        "43.6, 180.1, 5, distance",
        "43.6, -180.1, 5, distance",
        "43.6, -79.4, 0, distance",
        "43.6, -79.4, -1, distance",
        "43.6, -79.4, 50.1, distance",
        "43.6, -79.4, 5, popularity",
    })
    void invalidParametersAreRejected(String lat, String lng, String radiusKm, String sort) throws Exception {
        mockMvc.perform(get("/api/theatres/nearby")
                        .param("lat", lat).param("lng", lng).param("radiusKm", radiusKm)
                        .param("movieId", "1").param("sort", sort))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nearbyTheaterService);
    }

    @Test
    void radiusOfExactlyFiftyIsAllowed() throws Exception {
        when(nearbyTheaterService.findNearbyTheaters(anyDouble(), anyDouble(), eq(50.0), anyLong(), eq(NearbyTheaterSort.DISTANCE), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/theatres/nearby")
                        .param("lat", "43.6").param("lng", "-79.4").param("radiusKm", "50").param("movieId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void missingMovieIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/theatres/nearby").param("lat", "43.6").param("lng", "-79.4"))
                .andExpect(status().isBadRequest());
    }
}
