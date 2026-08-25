package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsBillSummaryResponse;
import com.cloudcostai.aws.dto.response.AwsBillingServiceCostResponse;

import java.util.List;

public interface AwsBillService {
    AwsBillSummaryResponse getBillSummary(String connectionToken);

    AwsBillSummaryResponse syncBilling(String connectionToken);

    List<AwsBillingServiceCostResponse> getServiceBreakdown(String connectionToken);
}
