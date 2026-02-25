package uk.gov.hmcts.cp.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class CaseMapperResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String caseId;

    private String caseUrn;

}
