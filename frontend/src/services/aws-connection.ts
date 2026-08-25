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
  };
  ec2Instances: Array<{ instanceId: string; name: string; instanceType: string; state: string; availabilityZone: string; region: string; privateIp: string; publicIp: string; launchTime: string }>;
  ebsVolumes: Array<{ volumeId: string; volumeType: string; size: number; state: string; availabilityZone: string; attachedInstanceId: string | null }>;
  s3Buckets: Array<{ bucketName: string; creationDate: string; region: string }>;
  rdsDatabases: Array<{ identifier: string; engine: string; engineVersion: string; instanceClass: string; status: string; availabilityZone: string; endpoint: string | null; region: string }>;
  lambdaFunctions: Array<{ functionName: string; runtime: string; memorySize: number | null; timeout: number | null; lastModified: string; functionArn: string; region: string }>;
  vpcs: Array<{ vpcId: string; cidrBlock: string; state: string; defaultVpc: boolean; region: string }>;
  securityGroups: Array<{ groupId: string; groupName: string; description: string; vpcId: string }>;
};

export async function fetchAwsResources(connectionToken: string): Promise<AwsResourceResponse> {
  const response = await axios.get<ApiResponse<AwsResourceResponse>>(`${apiUrl}/api/aws/resources`, {
    headers: { "X-AWS-Connection-Token": connectionToken },
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
  } | null;
  activeResources: { value: number };
  estimatedSavings: { value: number; currency: string; period: string } | null;
  budgetUsage: {
    configured: boolean;
    usagePercentage: number | null;
    budgetLimit: number | null;
    actualSpend: number;
    currency: string;
    status: string;
  } | null;
  costTrend: Array<{ month: string; cost: number }>;
  serviceDistribution: Array<{ name: string; value: number }>;
};

export async function fetchAwsDashboard(connectionToken: string): Promise<AwsDashboardResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardResponse>>(`${apiUrl}/api/aws/dashboard`, {
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function getAwsConnection(): Promise<AwsConnectionResponse | null> {
  const response = await axios.get<ApiResponse<AwsConnectionResponse | null>>(`${apiUrl}/api/aws/connection`);
  return response.data.data;
}

export async function disconnectAwsAccount(): Promise<void> {
  await axios.post(`${apiUrl}/api/aws/disconnect`);
}

export type AwsCostAnalyticsResponse = {
  accountId: string;
  currency: string;
  period: { start: string; end: string };
  summary: { totalCost: number; thisMonth: number; dailyAverage: number; forecast: number | null; savings: number | null };
  dailyTrend: Array<{ date: string; cost: number }>;
  dailyServiceCosts: Array<{ date: string; services: Array<{ name: string; cost: number }> }>;
  serviceCosts: Array<{ name: string; cost: number }>;
  regionCosts: Array<{ name: string; cost: number }>;
  topCostDrivers: Array<{ service: string; cost: number; percentage: number }>;
  anomalies: Array<{ date: string; cost: number; percentageAboveAverage: number }>;
  budget: { configured: boolean; limit: number | null; actualSpend: number; usagePercentage: number | null; currency: string };
  potentialSavings: { available: boolean; monthlySavings: number | null };
  dataStatus: string;
};

export async function fetchAwsCostAnalytics(connectionToken: string, period: string): Promise<AwsCostAnalyticsResponse> {
  const response = await axios.get<ApiResponse<AwsCostAnalyticsResponse>>(`${apiUrl}/api/aws/cost-analytics`, {
    params: { period },
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
};

export type AwsInsightResponse = {
  accountId: string;
  currency: string;
  generatedAt: string;
  insights: AwsInsight[];
};

export async function fetchAwsInsights(connectionToken: string): Promise<AwsInsightResponse> {
  const response = await axios.get<ApiResponse<AwsInsightResponse>>(`${apiUrl}/api/aws/insights`, {
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export type AwsRecommendation = {
  id: string;
  service: string;
  region: string;
  resource: string;
  currentCost: number;
  problem: string;
  reason: string;
  recommendation: string;
  recommendedAction: string;
  estimatedMonthlySaving: number | null;
  priority: "Low" | "Medium" | "High";
  category: string;
  status: "New" | "Applied" | "Dismissed";
  createdAt: string;
  resourcePath: string;
};

export type AwsRecommendationResponse = {
  accountId: string;
  currency: string;
  recommendations: AwsRecommendation[];
};

export async function fetchAwsRecommendations(connectionToken: string): Promise<AwsRecommendationResponse> {
  const response = await axios.get<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/recommendations`, {
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function generateAwsRecommendations(connectionToken: string): Promise<AwsRecommendationResponse> {
  const response = await axios.post<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/recommendations/generate`, null, {
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function updateAwsRecommendationStatus(connectionToken: string, id: string, status: AwsRecommendation["status"]): Promise<AwsRecommendationResponse> {
  const response = await axios.put<ApiResponse<AwsRecommendationResponse>>(`${apiUrl}/api/recommendations/${encodeURIComponent(id)}/status`, null, {
    params: { value: status },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export type AwsDashboardChartResponse = {
  accountId: string;
  currency: string;
  range: string;
  points: Array<{ period: string; cost: number }>;
  services: Array<{ service: string; cost: number; percentage: number }>;
  totalCost: number | null;
};

export async function fetchAwsCostTrend(connectionToken: string, range: string): Promise<AwsDashboardChartResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardChartResponse>>(`${apiUrl}/api/aws/dashboard/cost-trend`, {
    params: { range },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function fetchAwsServiceDistribution(connectionToken: string, range: string): Promise<AwsDashboardChartResponse> {
  const response = await axios.get<ApiResponse<AwsDashboardChartResponse>>(`${apiUrl}/api/aws/dashboard/service-distribution`, {
    params: { range },
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}