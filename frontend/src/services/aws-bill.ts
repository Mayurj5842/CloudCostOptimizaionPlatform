import axios from "axios";


type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type AwsBillSummaryResponse = {
  accountId: string;
  billingPeriod: { start: string; end: string };
  billStatus: string;
  currency: string;
  serviceProvider: string;
  totalAmount: number;
  estimatedGrandTotal: number;
  paymentInformation: string;
};

export async function fetchAwsBillSummary(connectionToken: string): Promise<AwsBillSummaryResponse> {
  const response = await axios.get<ApiResponse<AwsBillSummaryResponse>>(`${apiUrl}/api/billing/summary`, {
    headers: { "X-AWS-Connection-Token": connectionToken },
  });
  return response.data.data;
}

export async function syncAwsBilling(connectionToken: string): Promise<AwsBillSummaryResponse> {
  try {
    const response = await axios.post<ApiResponse<AwsBillSummaryResponse>>(`${apiUrl}/api/billing/sync`, null, {
      headers: { "X-AWS-Connection-Token": connectionToken },
    });
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      if (error.response?.status === 401) {
        throw new Error("AWS connection expired. Please reconnect your AWS account.");
      }
      const message = error.response?.data?.message;
      const text = typeof message === "string" && message.trim() ? message : error.message;
      if (/S3_BUCKET is not configured|S3_PREFIX is not configured/i.test(text)) {
        throw new Error("CUR 2.0 S3 billing export is not configured for this deployment. Set AWS_BILLING_S3_BUCKET and AWS_BILLING_S3_PREFIX to enable CUR sync, or use Generate recommendations from the stored billing data instead.");
      }
      throw new Error(text);
    }
    throw error;
  }
}
