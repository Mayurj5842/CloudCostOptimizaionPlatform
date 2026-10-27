import axios from "axios";

export type AwsConnectionResponse = {
  accountAlias: string;
  accountId: string;
  arn: string;
  userId: string;
  region: string;
  provider: "AWS";
  connectionStatus: "CONNECTED" | "DISCONNECTED";
  connectionToken?: string | null;
};

type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function getAwsHeaders(connectionToken?: string | null): Record<string, string> {
  const token = connectionToken?.trim();
  if (!token) {
    throw new Error("AWS connection is missing. Please reconnect to AWS.");
  }
  return { "X-AWS-Connection-Token": token };
}

export async function connectAwsAccount(credentials: {
  accountAlias: string;
  accessKey: string;
  secretKey: string;
  region: string;
}): Promise<AwsConnectionResponse & { connectionToken: string }> {
  const response = await axios.post<ApiResponse<AwsConnectionResponse & { connectionToken: string }>>(`${apiUrl}/api/aws/connect`, credentials);
  return response.data.data;
}

export type AwsResourceResponse = {
  accountId: string;
  region: string;
  summary: {
    totalResources: number;
    activeResources: number;
    ec2Instances: number;
    runningEc2Instances: number;
    stoppedEc2Instances: number;
    ebsVolumes: number;
    attachedEbsVolumes: number;
    unattachedEbsVolumes: number;
    s3Buckets: number;
    rdsDatabases: number;
    availableRdsDatabases: number;
    stoppedRdsDatabases: number;
    lambdaFunctions: number;
    vpcs: number;
    securityGroups: number;
    healthyResources: number;
    unusedVolumes: number;
  };
  ec2Instances: Array<{ instanceId: string; name: string | null; instanceType: string | null; state: string | null; availabilityZone: string | null; region: string; privateIp: string | null; publicIp: string | null; launchTime: string | null; tags: Array<{ key: string; value: string }>; platform: string | null }>;
  ebsVolumes: Array<{ volumeId: string; volumeType: string | null; size: number | null; state: string | null; availabilityZone: string | null; attachedInstanceId: string | null; creationTime: string | null; encrypted: boolean | null; region?: string | null; tags: Array<{ key: string; value: string }> }>;
  s3Buckets: Array<{ bucketName: string; creationDate: string | null; region: string | null }>;
  rdsDatabases: Array<{ identifier: string; engine: string | null; engineVersion: string | null; instanceClass: string | null; status: string | null; availabilityZone: string | null; endpoint: string | null; region: string; allocatedStorage: number | null; creationTime: string | null; multiAz: boolean | null }>;
  lambdaFunctions: Array<{ functionName: string; runtime: string | null; memorySize: number | null; timeout: number | null; lastModified: string | null; functionArn: string; region: string; handler: string | null; state: string | null; runtimeStatus: string | null }>;
  vpcs: Array<{ vpcId: string; cidrBlock: string | null; state: string | null; defaultVpc: boolean; region: string; name: string | null }>;
  securityGroups: Array<{ groupId: string; groupName: string | null; description: string | null; vpcId: string | null; region: string | null; inboundRuleCount: number | null; outboundRuleCount: number | null }>;
  recentResources: Array<{ resourceType: string; resourceId: string; name: string | null; timestamp: string; status: string | null }>;
};

export async function fetchAwsResources(connectionToken: string): Promise<AwsResourceResponse> {
  const response = await axios.get<ApiResponse<AwsResourceResponse>>(`${apiUrl}/api/aws/resources`, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export type AwsDashboardResponse = {
  accountId: string;
  totalCloudSpend: {
    value: number;
    currency: string;
    period: string;
    previousMonth: number;
    percentageChange: number | null;
    hasPreviousMonthData: boolean;
    previousMonthSpend?: number;
    changePercentage?: number | null;
    changeDirection?: "INCREASED" | "DECREASED" | "UNCHANGED" | "NO_COMPARABLE_PREVIOUS_MONTH_SPEND" | "PREVIOUS_MONTH_DATA_UNAVAILABLE";
    dataStatus?: string;
  } | null;
  activeResources: { value: number };
  estimatedSavings: { value: number; currency: string; period: string } | null;
  budgetUsage: {
    configured: boolean;
    usagePercentage: number | null;
    budgetLimit: number | null;
    actualSpend: number | null;
    currency: string;
    status: string;
  } | null;
  forecast: {
    value: number | null;
    currency: string;
    period: string;
    actualToDate: number | null;
    dailyRunRate: number | null;
    projectedRemaining: number | null;
    previousMonth: number | null;
    percentageChange: number | null;
    daysObserved: number;
    daysInMonth: number;
    remainingDays: number;
    method: "RUN_RATE_PROJECTION" | "FINAL_ACTUAL" | "PRIOR_MONTH_RUN_RATE" | "NO_BILLING_DATA";
    dataStatus?: "AVAILABLE" | "NO_BILLING_DATA";
  } | null;
  costTrend: Array<{ month: string; cost: number }>;
  serviceDistribution: Array<{ name: string; value: number }>;
  billing: {
    status: "AVAILABLE" | "UPDATING" | "STALE" | "NO_DATA" | "ERROR";
    dataSource: string;
    lastUpdated: string | null;
    billingPeriodStart: string | null;
    billingPeriodEnd: string | null;
    message: string | null;
  };
};

export async function fetchAwsDashboard(connectionToken: string, month?: string): Promise<AwsDashboardResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardResponse>>(`${apiUrl}/api/aws/dashboard`, {
    params: month ? { month } : undefined,
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function getAwsConnection(connectionToken: string): Promise<AwsConnectionResponse | null> {
  const response = await axios.get<ApiResponse<AwsConnectionResponse | null>>(`${apiUrl}/api/aws/connection`, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export type AwsConnectionTestResponse = { accountId: string; region: string; status: "HEALTHY"; message: string };

export async function testAwsConnection(connectionToken: string): Promise<AwsConnectionTestResponse> {
  const response = await axios.get<ApiResponse<AwsConnectionTestResponse>>(`${apiUrl}/api/aws/connection/test`, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function disconnectAwsAccount(connectionToken: string): Promise<void> {
  await axios.post(`${apiUrl}/api/aws/disconnect`, undefined, {
    headers: getAwsHeaders(connectionToken),
  });
}

export type DiagnosticStatus = "HEALTHY" | "WARNING" | "FAILED" | "UNAVAILABLE";
export type SystemDiagnosticsResponse = { checkedAt: string; checks: Array<{ key: string; label: string; status: DiagnosticStatus; detail: string }> };

export async function fetchSystemDiagnostics(connectionToken: string): Promise<SystemDiagnosticsResponse> {
  const response = await axios.get<ApiResponse<SystemDiagnosticsResponse>>(`${apiUrl}/api/system/diagnostics`, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export type Budget = {
  id: string;
  name: string;
  awsAccountId: string;
  amount: number;
  actualSpend: number | null;
  remainingAmount: number | null;
  usagePercentage: number | null;
  currency: string;
  status: string;
  period: string;
  thresholds: number[];
  createdAt: string;
  updatedAt: string;
  serviceName: string | null;
  enabled: boolean;
  forecastSpend: number | null;
  projectedVariance: number | null;
  forecastAvailable: boolean;
  forecastMessage: string;
  spikeThresholdPercent: number;
};

export type BudgetAlert = {
  id: string;
  budgetId: string;
  type: string;
  threshold: number | null;
  message: string;
  actualSpend: number | null;
  budgetAmount: number | null;
  currency: string | null;
  read: boolean;
  createdAt: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  title: string;
  budgetName: string | null;
  serviceName: string | null;
  alertPeriod: string;
  comparisonPeriod: string | null;
  percentageChange: number | null;
};

type BudgetRequest = {
  name: string;
  awsAccountId: string;
  amount: number;
  currency: string;
  period: string;
  thresholds: number[];
  month?: string;
  serviceName?: string | null;
  enabled?: boolean;
  spikeThresholdPercent?: number;
};

export type BudgetAlertFilters = {
  month?: string;
  severity?: string;
  type?: string;
  from?: string;
  to?: string;
};

export async function fetchBudgets(connectionToken: string, month?: string): Promise<Budget[]> {
  const response = await axios.get<ApiResponse<Budget[]>>(`${apiUrl}/api/budgets`, {
    params: month ? { month } : undefined,
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function fetchBudgetAlerts(connectionToken: string, month?: string): Promise<BudgetAlert[]> {
  return fetchBudgetAlertsFiltered(connectionToken, { month });
}

export async function fetchBudgetAlertsFiltered(connectionToken: string, filters: BudgetAlertFilters): Promise<BudgetAlert[]> {
  const response = await axios.get<ApiResponse<BudgetAlert[]>>(`${apiUrl}/api/alerts`, {
    params: Object.values(filters).some(Boolean) ? filters : undefined,
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function fetchBudgetServices(connectionToken: string, month?: string): Promise<string[]> {
  const response = await axios.get<ApiResponse<string[]>>(`${apiUrl}/api/budgets/services`, {
    params: month ? { month } : undefined,
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function createBudget(connectionToken: string, request: BudgetRequest): Promise<Budget> {
  const normalizedName = request.name?.trim();
  const normalizedAmount = Number(request.amount);
  const normalizedThresholds = Array.isArray(request.thresholds)
    ? request.thresholds.filter((value) => Number.isFinite(value) && value > 0 && value <= 100)
    : [];

  if (!normalizedName) {
    throw new Error("Budget name is required.");
  }

  if (!request.awsAccountId?.trim()) {
    throw new Error("AWS account is required.");
  }

  if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
    throw new Error("Budget amount must be greater than 0.");
  }

  const payload = {
    ...request,
    name: normalizedName,
    amount: normalizedAmount,
    thresholds: normalizedThresholds,
    month: request.month,
  };

  const response = await axios.post<ApiResponse<Budget>>(`${apiUrl}/api/budgets`, payload, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function updateBudget(connectionToken: string, id: string, request: BudgetRequest): Promise<Budget> {
  const response = await axios.put<ApiResponse<Budget>>(`${apiUrl}/api/budgets/${id}`, request, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function deleteBudget(connectionToken: string, id: string): Promise<void> {
  await axios.delete(`${apiUrl}/api/budgets/${id}`, {
    headers: getAwsHeaders(connectionToken),
  });
}

export async function markBudgetAlertRead(connectionToken: string, id: string): Promise<void> {
  await axios.patch(`${apiUrl}/api/alerts/${id}/read`, undefined, {
    headers: getAwsHeaders(connectionToken),
  });
}

export type AwsCostAnalyticsResponse = {
  accountId: string;
  currency: string;
  period: { start: string; end: string };
  summary: { totalCost: number | null; thisMonth: number | null; dailyAverage: number | null; forecast: number | null; savings: number | null; previousPeriodCost: number | null; difference: number | null; percentageChange: number | null };
  dailyTrend: Array<{ date: string; cost: number }>;
  dailyServiceCosts: Array<{ date: string; services: Array<{ name: string; cost: number }> }>;
  serviceCosts: Array<{ name: string; cost: number }>;
  regionCosts: Array<{ name: string; cost: number }>;
  topCostDrivers: Array<{ service: string; cost: number; percentage: number }>;
  anomalies: Array<{ date: string; cost: number; percentageAboveAverage: number }>;
  budget: { configured: boolean; limit: number | null; actualSpend: number | null; usagePercentage: number | null; currency: string };
  potentialSavings: { available: boolean; monthlySavings: number | null };
  dataStatus: "AVAILABLE" | "NO_CUR_DATA" | "UNAVAILABLE";
  billingPeriod: string;
  dataSource: "CUR_2_0" | "COST_EXPLORER" | "MIXED" | "AWS_CUR_2_0" | "CUR_POSTGRESQL" | "DATABASE_CACHE";
  totalCost: number | null;
  lastUpdated: string | null;
  status: string;
  curDelivery: string | null;
  curFiles: string[];
  curRowsUsed: number;
};

export async function fetchAwsCostAnalytics(connectionToken: string, period: string, month?: string): Promise<AwsCostAnalyticsResponse> {
  const response = await axios.get<ApiResponse<AwsCostAnalyticsResponse>>(`${apiUrl}/api/aws/cost-analytics`, {
    params: { period, ...(month ? { month } : {}) },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export type AwsInsight = {
  id: string;
  severity: "Low" | "Medium" | "High";
  category: string;
  service: string;
  region: string;
  resource: string;
  currentCost: number;
  problemDetected: string;
  reason: string;
  estimatedImpact: number;
  recommendedAction: string;
  resourcePath: string;
  type: string;
  previousCost: number | null;
  percentage: number | null;
  resourceType: string | null;
  resourceCount: number | null;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  dataSource: string;
};

export type AwsInsightResponse = {
  accountId: string;
  currency: string;
  generatedAt: string;
  insights: AwsInsight[];
  recommendations: AwsRecommendation[];
  billingPeriod: string;
  dataSource: "AWS_CUR_2_0" | "DATABASE_CACHE";
  status: string;
  noDataReason: string | null;
  totalSpend: number | null;
  billingPeriodStart: string | null;
  billingPeriodEnd: string | null;
  lastUpdated: string | null;
  message: string | null;
};

export async function fetchAwsInsights(connectionToken: string, month?: string): Promise<AwsInsightResponse> {
  try {
    const response = await axios.get<ApiResponse<AwsInsightResponse>>(`${apiUrl}/api/aws/insights`, {
      params: month ? { month } : undefined,
      headers: { "X-AWS-Connection-Token": connectionToken },
    });
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      if (error.response?.status === 401) throw new Error("Your AWS connection session is no longer valid. Please reconnect.");
      if (error.response?.status === 403) throw new Error("Your AWS account connection is unavailable.");
      if (error.response?.status === 500) throw new Error("Billing data could not be loaded. Please try again.");
      if (error.response?.status === 400) {
        const message = typeof error.response.data === "object" && error.response.data !== null && "message" in error.response.data
          ? String(error.response.data.message)
          : "Insights could not be calculated from the available billing data.";
        if (message.toLowerCase().includes("expired")) throw new Error("Your AWS connection session is no longer valid. Please reconnect.");
        if (message.toLowerCase().includes("connection")) throw new Error("Your AWS account is not connected.");
        throw new Error(message);
      }
      throw new Error(error.response?.data.message ?? error.message);
    }
    throw error;
  }
}

export type AwsRecommendation = {
  id: string;
  service: string;
  region: string;
  resource: string;
  currentCost: number | null;
  problem: string;
  reason: string;
  recommendation: string;
  recommendedAction: string;
  estimatedMonthlySaving: number | null;
  priority: "LOW" | "MEDIUM" | "HIGH" | "Low" | "Medium" | "High";
  category: string;
  status: "NEW" | "REVIEWED" | "DISMISSED" | "New" | "Applied" | "Dismissed";
  createdAt: string;
  resourcePath: string;
  resourceId: string | null;
  evidence: string | null;
  billingMonth: string;
  dataSource: string;
  costSharePercentage: number | null;
  periodTotalCost: number | null;
  concentrationThreshold: number | null;
  resourceState: string | null;
  savingsBasis: "HEURISTIC_ESTIMATE" | "INSUFFICIENT_DATA" | string;
};

export type AwsRecommendationResponse = {
  accountId: string;
  currency: string;
  recommendations: AwsRecommendation[];
  billingMonth: string;
  status: "SUCCESS" | "NO_DATA" | "NO_RESOURCES" | "NO_RECOMMENDATIONS" | "NO_RESOURCE_BILLING_MATCH" | "INSUFFICIENT_BILLING_EVIDENCE" | "PERMISSION_DENIED" | "RESOURCE_UNAVAILABLE";
  message: string | null;
  totalSpend: number | null;
  analysisComplete: boolean;
  resourceDataStatus: "AVAILABLE" | "UNAVAILABLE" | "NO_RESOURCES" | "PERMISSION_DENIED" | "NOT_CHECKED";
  evaluationReasons: string[];
};

export async function fetchAwsRecommendations(connectionToken: string, month?: string): Promise<AwsRecommendationResponse> {
  try {
    const response = await axios.get<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/aws/recommendations`, {
      params: month ? { month } : undefined,
      headers: getAwsHeaders(connectionToken),
    });
    return response.data.data;
  } catch (error) {
    throw recommendationApiError(error);
  }
}

export type AwsCostSimulationResponse = {
  accountId: string;
  billingMonth: string;
  currency: string | null;
  dataSource: string;
  mode: "RECOMMENDATIONS" | "WHAT_IF" | string;
  status: "SUCCESS" | "NO_BILLING_DATA" | "INSUFFICIENT_PRICING_DATA" | string;
  message: string | null;
  currentMonthlyCost: number | null;
  estimatedMonthlyCost: number | null;
  estimatedMonthlySaving: number | null;
  estimatedAnnualSaving: number | null;
  whatIfReductionPercent: number | null;
  scenarios: Array<{
    recommendationId: string | null;
    title: string;
    service: string | null;
    resourceId: string | null;
    region: string | null;
    status: "AVAILABLE" | "INSUFFICIENT_PRICING_DATA" | "ALREADY_APPLIED" | "NOT_ACTIONABLE" | string;
    estimateBasis: string | null;
    estimatedMonthlySaving: number | null;
    message: string | null;
  }>;
};

export async function simulateAwsCost(connectionToken: string, month: string, recommendationIds: string[], whatIfReductionPercent?: number | null): Promise<AwsCostSimulationResponse> {
  const response = await axios.post<ApiResponse<AwsCostSimulationResponse>>(`${apiUrl}/api/aws/optimization/simulator`, {
    month,
    recommendationIds: recommendationIds.length ? recommendationIds : null,
    whatIfReductionPercent: whatIfReductionPercent ?? null,
  }, {
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export async function generateAwsRecommendations(connectionToken: string, month?: string): Promise<AwsRecommendationResponse> {
  try {
    const response = await axios.post<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/aws/recommendations/generate`, null, {
      params: month ? { month } : undefined,
      headers: getAwsHeaders(connectionToken),
    });
    return response.data.data;
  } catch (error) {
    throw recommendationApiError(error);
  }
}

export async function updateAwsRecommendationStatus(connectionToken: string, id: string, status: AwsRecommendation["status"], month?: string): Promise<AwsRecommendationResponse> {
  try {
    const response = await axios.put<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/aws/recommendations/${encodeURIComponent(id)}/status`, null, {
      params: { value: status, ...(month ? { month } : {}) },
      headers: getAwsHeaders(connectionToken),
    });
    return response.data.data;
  } catch (error) {
    throw recommendationApiError(error);
  }
}

function recommendationApiError(error: unknown): Error {
  if (!axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error instanceof Error ? error : new Error("Recommendation request failed.");
  }
  if (error.response?.status === 401) {
    return new Error("AWS connection expired. Please reconnect your AWS account.");
  }
  const message = error.response?.data?.message;
  return new Error(typeof message === "string" && message.trim() ? message : error.message);
}

export type AwsDashboardChartResponse = {
  accountId: string;
  currency: string;
  range: string;
  points: Array<{ period: string; cost: number }>;
  services: Array<{ service: string; cost: number; percentage: number }>;
  totalCost: number | null;
  dataStatus: "AVAILABLE" | "NO_BILLING_DATA" | "UNAVAILABLE";
  dataSource: "CUR_2_0" | "COST_EXPLORER" | string;
};

export async function fetchAwsCostTrend(connectionToken: string, range: string, month?: string): Promise<AwsDashboardChartResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardChartResponse>>(`${apiUrl}/api/aws/dashboard/cost-trend`, {
    params: { range, ...(month ? { month } : {}) },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function fetchAwsServiceDistribution(connectionToken: string, range: string, month?: string): Promise<AwsDashboardChartResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardChartResponse>>(`${apiUrl}/api/aws/dashboard/service-distribution`, {
    params: { range, ...(month ? { month } : {}) },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export type AwsReportResponse = {
  accountId: string;
  status: "SUCCESS" | "NO_DATA" | "PARTIAL" | "ERROR";
  dataSource: string;
  billingMonth: string;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  generatedAt: string;
  lastUpdated: string | null;
  currency: string;
  summary: { totalCost: number | null; previousMonthCost: number | null; changeAmount: number | null; changePercentage: number | null };
  serviceBreakdown: Array<{ name: string; cost: number }>;
  costTrend: Array<{ date: string; cost: number }>;
  topCostDrivers: Array<{ service: string; cost: number; percentage: number }>;
  resourceSummary: AwsResourceResponse["summary"] | null;
  resourceStatus: string;
  insights: AwsInsight[];
  recommendations: AwsRecommendation[];
  savingsSummary: { potentialMonthlySavings: number | null; available: boolean };
  message: string | null;
  budgets: Budget[];
  alerts: BudgetAlert[];
  availableServices?: string[];
  availableRegions?: string[];
};

export type AwsReportRequestParams = {
  month?: string;
  startDate?: string;
  endDate?: string;
  granularity?: string;
  service?: string;
  region?: string;
  groupBy?: string;
};

export async function fetchAwsReport(connectionToken: string, params: string | AwsReportRequestParams): Promise<AwsReportResponse> {
  const requestParams: Record<string, unknown> = typeof params === "string" ? { month: params } : { ...params };
  const response = await axios.get<ApiResponse<AwsReportResponse>>(`${apiUrl}/api/aws/reports`, {
    params: requestParams,
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function downloadAwsReportPdf(connectionToken: string, params: string | AwsReportRequestParams): Promise<Blob> {
  try {
    const requestParams: Record<string, unknown> = typeof params === "string" ? { month: params } : { ...params };
    const response = await axios.get(`${apiUrl}/api/aws/reports/pdf`, {
      params: requestParams,
      responseType: "blob",
      headers: getAwsHeaders(connectionToken),
    });
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      throw new Error("AWS connection expired. Reconnect your AWS account and try again.");
    }
    if (axios.isAxiosError(error) && error.response?.status === 422) {
      throw new Error("No CUR billing data is available for the selected month.");
    }
    throw error;
  }
}

export type AwsRecentActivityResponse = { activities: Array<{ id: string; service: string; action: string; message: string; resourceId: string; region: string; timestamp: string }> };

export async function fetchAwsRecentActivity(connectionToken: string, limit = 10): Promise<AwsRecentActivityResponse> {
  const response = await axios.get<ApiResponse<AwsRecentActivityResponse>>(`${apiUrl}/api/aws/recent-activity`, {
    params: { limit },
    headers: getAwsHeaders(connectionToken),
  });
  return response.data.data;
}

export type AwsLiveCostResponse = {
  accountId: string;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  currency: string;
  cost: number | null;
  regions: Array<{ region: string; cost: number; currency: string; fetchedAt: string }>;
  services: Array<{ service: string; cost: number; currency: string; fetchedAt: string }>;
  dataSource: "COST_EXPLORER";
  fetchedAt: string;
  status: "SUCCESS" | "NO_DATA" | string;
  message: string | null;
};

export async function fetchLiveAwsCost(connectionToken: string, month: string): Promise<AwsLiveCostResponse> {
  try {
    const response = await axios.get<ApiResponse<AwsLiveCostResponse>>(`${apiUrl}/api/aws/live-cost`, {
      params: { month },
      headers: getAwsHeaders(connectionToken),
    });
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      if (error.response?.status === 401) throw new Error("Your AWS connection session is no longer valid. Please reconnect.");
      if (error.response?.status === 403) throw new Error("AWS Cost Explorer permission is unavailable.");
      throw new Error(error.response?.data.message ?? "AWS Cost Explorer could not fetch live cost data.");
    }
    throw error;
  }
}