package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProgressionResponse {
    private ProsecutionCase prosecutionCase;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ProsecutionCase {
        private String caseStatus;
        private List<Defendant> defendants;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class Defendant {
            private List<Offence> offences;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class Offence {
            private List<ReportingRestriction> reportingRestrictions;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class ReportingRestriction {
            private String id;
            private String orderedDate;
        }
    }

}
