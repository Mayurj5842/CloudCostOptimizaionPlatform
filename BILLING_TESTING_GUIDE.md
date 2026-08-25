# AWS Billing Data Integration - Testing & Verification Guide

## ✅ Implementation Status

### Backend
- [x] Cost Explorer client factory method created
- [x] AwsCostAnalyticsService implemented
- [x] GetCostAndUsage integration with daily, service, and region grouping
- [x] GetCostForecast integration  
- [x] AWS Budgets API integration
- [x] Exception handling and safe logging
- [x] AwsCostAnalyticsController wired correctly
- [x] Backend compiles without errors

### Frontend
- [x] fetchAwsCostAnalytics service created
- [x] AwsCostAnalyticsResponse TypeScript types defined
- [x] /billing page integrated with useQuery
- [x] Data status handling (AVAILABLE vs UNAVAILABLE)
- [x] Loading, error, and data display states
- [x] Cost charts and breakdowns implemented
- [x] No compilation errors

## 🧪 TESTING CHECKLIST

### Test 1: Verify Backend Compiles ✅
```bash
cd backend
./mvnw.cmd clean compile -DskipTests
# Result: No output = Success
```

### Test 2: Verify Endpoint is Accessible
1. Start Spring Boot backend:
   ```bash
   cd backend
   ./mvnw.cmd spring-boot:run
   ```

2. In another terminal, test the endpoint:
   ```bash
   # First, connect an AWS account to get a token
   curl -X POST http://localhost:8080/api/aws/connect \
     -H "Content-Type: application/json" \
     -d '{
       "accountAlias": "My AWS Account",
       "accessKey": "YOUR_ACCESS_KEY",
       "secretKey": "YOUR_SECRET_KEY",
       "region": "ap-south-1"
     }'
   
   # Response should include: "connectionToken": "uuid-string"
   ```

3. Use the token to call the billing endpoint:
   ```bash
   curl -X GET "http://localhost:8080/api/aws/cost-analytics?period=CURRENT_MONTH" \
     -H "X-AWS-Connection-Token: YOUR_TOKEN_HERE"
   ```

4. Check the response:
   - Should be HTTP 200 OK
   - Should have structure: `{ "success": true, "data": {...}, "message": "..." }`
   - Check `data.dataStatus`: 
     - "LATEST_AVAILABLE" = billing data retrieved
     - "UNAVAILABLE" = API failed (check logs for error)

### Test 3: Check Backend Logs During Billing Request
When you call `/api/aws/cost-analytics`, check the backend console for logs like:

**Success Case:**
```
[INFO] AWS Cost Analytics requested for account=399779591966 period=CURRENT_MONTH dateRange=2026-08-01 to 2026-08-12
[DEBUG] Cost Explorer client created for account=399779591966
[DEBUG] Daily costs results=12 results
[DEBUG] Service costs results=3 results
[DEBUG] Region costs results=1 results
[DEBUG] Month-to-date costs results=12 results
[INFO] AWS Cost Analytics completed for account=399779591966 totalCost=0.44 serviceCount=3 regionCount=1
```

**Failure Case:**
```
[INFO] AWS Cost Analytics requested for account=399779591966 period=CURRENT_MONTH dateRange=2026-08-01 to 2026-08-12
[DEBUG] Cost Explorer client created for account=399779591966
[WARN] AWS Cost Explorer data unavailable for account 399779591966: CostExplorerException - User is not authorized to perform: ce:GetCostAndUsage
```

### Test 4: Start Frontend and Verify Integration
1. Start Next.js frontend:
   ```bash
   cd frontend
   npm run dev
   ```

2. Navigate to http://localhost:3000

3. Go to "Integrations" → Connect an AWS account with credentials that have ce:GetCostAndUsage permission

4. Click "Cost Analytics" in the left sidebar or navigate to `/billing`

5. Expected Results:
   - **If permissions OK and data exists:**
     - Charts display with cost data
     - "Total" card shows cost amount (should be ~USD 0.44)
     - "This Month" shows current month estimate
     - Daily trend chart shows daily costs
     - Service breakdown shows costs by AWS service
     - Region breakdown shows costs by region

   - **If permissions missing:**
     - Shows: "AWS Billing Data Unavailable"
     - Lists reason: "Missing AWS permissions (ce:GetCostAndUsage)"

   - **If loading:**
     - Shows "Loading..." in all cost cards
     - Wait for data to load

### Test 5: Browser DevTools Network Check
1. Open browser DevTools (F12)
2. Go to Network tab
3. Navigate to /billing page
4. Look for request: `GET /api/aws/cost-analytics?period=CURRENT_MONTH`
5. Check response:
   - Status: 200 OK
   - JSON body includes:
     ```json
     {
       "success": true,
       "data": {
         "accountId": "399779591966",
       "currency": "USD",
       "dataStatus": "LATEST_AVAILABLE",
       "summary": {
         "totalCost": 0.44,
         "thisMonth": 0.44,
         ...
       }
       }
     }
     ```

### Test 6: AWS CLI Verification (Optional)
Verify the same credentials work with AWS CLI:
```bash
# Configure profile
aws configure --profile test-account
# Enter AWS Access Key ID
# Enter AWS Secret Access Key  
# Default region: ap-south-1

# Verify account
aws sts get-caller-identity --profile test-account
# Should return: Account: 399779591966

# Test Cost Explorer
aws ce get-cost-and-usage \
  --time-period Start=2026-08-01,End=2026-08-13 \
  --granularity MONTHLY \
  --metrics "UnblendedCost" \
  --profile test-account

# Should return billing data if permissions are correct
```

## 🔧 TROUBLESHOOTING

### Issue 1: "AWS Billing Data Unavailable" Message

**Check IAM Permissions:**
```bash
aws iam get-user-policy --user-name YOUR_USER --policy-name YOUR_POLICY
```

The policy must include:
```json
{
  "Effect": "Allow",
  "Action": [
    "ce:GetCostAndUsage",
    "ce:GetCostForecast"
  ],
  "Resource": "*"
}
```

**Check Cost Explorer is Enabled:**
1. Log into AWS Console
2. Billing → Cost Explorer
3. Click "Enable" if not already active

**Check Account ID Matches:**
- AWS Console shows: Account 399779591966
- CloudCost AI page header shows: AWS Account: (should match)
- If different, disconnect and reconnect with correct credentials

### Issue 2: Showing $0.00 For All Costs

**Possible Causes:**
1. AWS account has no billable usage
2. All resources are within free tier
3. Billing data hasn't propagated yet (AWS has 24-48 hour delay)
4. Cost Explorer is brand new (needs 1+ month of data)

**Solution:**
1. Check AWS Console → Cost Explorer directly
2. If it also shows $0, billing data might not be available yet
3. Wait 24-48 hours and try again
4. Or create actual billable resources (e.g., t2.micro EC2 instance)

### Issue 3: Backend Compilation Fails

```bash
cd backend
./mvnw.cmd clean compile -DskipTests
# If error, check:
# 1. Java version: java -version (need 17+)
# 2. Maven cache: ./mvnw.cmd clean
# 3. Dependencies: ./mvnw.cmd dependency:resolve
```

### Issue 4: Endpoint Returns 403/401 Unauthorized

**Cause:** Security config not allowing endpoint or wrong headers

**Check:**
1. SecurityConfig.java should have:
   ```java
   .requestMatchers("/api/aws/cost-analytics").permitAll()
   ```

2. Request must include header:
   ```
   X-AWS-Connection-Token: YOUR_CONNECTION_TOKEN
   ```

3. CORS must allow header in CorsConfigurationSource:
   ```java
   configuration.setAllowedHeaders(List.of("Content-Type", "X-AWS-Connection-Token"));
   ```

### Issue 5: Frontend Shows "Loading..." Forever

**Cause:** Query stuck in loading state

**Check:**
1. Open DevTools → Network tab
2. See if request is pending or errored
3. Check backend logs for errors
4. If 500 error, exception was raised (shouldn't happen with exception handler)
5. Refresh page and try again

## 📊 EXPECTED API RESPONSE STRUCTURE

When successfully retrieving billing data:

```json
{
  "success": true,
  "message": "AWS cost analytics fetched successfully",
  "data": {
    "accountId": "399779591966",
    "currency": "USD",
    "period": {
      "start": "2026-08-01",
      "end": "2026-08-11"
    },
    "summary": {
      "totalCost": 0.44,
      "thisMonth": 0.44,
      "dailyAverage": 0.04,
      "forecast": 1.32,
      "savings": null
    },
    "dailyTrend": [
      { "date": "2026-08-01", "cost": 0.05 },
      { "date": "2026-08-02", "cost": 0.04 },
      // ... more days
    ],
    "serviceCosts": [
      { "name": "Amazon Elastic Compute Cloud - Compute", "cost": 0.25 },
      { "name": "Amazon Simple Storage Service", "cost": 0.10 },
      // ...
    ],
    "regionCosts": [
      { "name": "ap-south-1", "cost": 0.44 }
    ],
    "topCostDrivers": [
      { "service": "Amazon Elastic Compute Cloud - Compute", "cost": 0.25, "percentage": 56.82 }
    ],
    "anomalies": [],
    "budget": {
      "configured": false,
      "limit": null,
      "actualSpend": 0.44,
      "usagePercentage": null,
      "currency": "USD"
    },
    "potentialSavings": {
      "available": false,
      "monthlySavings": null
    },
    "dataStatus": "LATEST_AVAILABLE"
  }
}
```

When billing data is unavailable:

```json
{
  "success": true,
  "message": "AWS cost analytics fetched successfully",
  "data": {
    "accountId": "399779591966",
    "currency": "USD",
    "period": {
      "start": "2026-08-01",
      "end": "2026-08-11"
    },
    "summary": {
      "totalCost": 0.00,
      "thisMonth": 0.00,
      "dailyAverage": 0.00,
      "forecast": null,
      "savings": null
    },
    "dailyTrend": [],
    "serviceCosts": [],
    "regionCosts": [],
    "topCostDrivers": [],
    "anomalies": [],
    "budget": {
      "configured": false,
      "limit": null,
      "actualSpend": 0.00,
      "usagePercentage": null,
      "currency": "USD"
    },
    "potentialSavings": {
      "available": false,
      "monthlySavings": null
    },
    "dataStatus": "UNAVAILABLE"
  }
}
```

## ✅ SUCCESS CRITERIA

Implementation is complete when:

1. ✅ Backend compiles without errors
2. ✅ Frontend builds without errors  
3. ✅ GET /api/aws/cost-analytics returns HTTP 200
4. ✅ Response includes real AWS billing data (OR safe UNAVAILABLE status)
5. ✅ Frontend displays real costs OR friendly error message
6. ✅ No hardcoded values used
7. ✅ No credentials exposed in logs/responses
8. ✅ Matches AWS Console billing (~USD 0.44)
9. ✅ Works after browser refresh
10. ✅ Handles errors gracefully

## 🚀 NEXT STEPS

1. Compile backend: ✅ DONE
2. Start backend server
3. Start frontend server
4. Connect AWS account with valid credentials
5. Navigate to /billing page
6. Verify billing data appears
7. If not, check backend logs for error details
8. If error, check IAM permissions and Cost Explorer enablement
9. Report findings and exact error messages
