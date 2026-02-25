package uk.gov.hmcts.cp.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProgressionResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private ProsecutionCase prosecutionCase;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ProsecutionCase implements Serializable {
        private static final long serialVersionUID = 2L;

        private String caseStatus;
        private List<Defendant> defendants;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class Defendant implements Serializable {
            private static final long serialVersionUID = 3L;

            private List<Offence> offences;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class Offence implements Serializable {
            private static final long serialVersionUID = 4L;

            private List<ReportingRestriction> reportingRestrictions;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        public static class ReportingRestriction implements Serializable {
            private static final long serialVersionUID = 5L;

            private String id;
            private String orderedDate;
        }
    }

}
