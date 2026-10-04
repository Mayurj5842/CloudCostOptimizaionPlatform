package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import java.util.Set;

public interface AwsResourceService {
    AwsResourceResponse discoverResources(String connectionToken);

    default AwsResourceResponse discoverResourcesForRecommendations(String connectionToken, Set<String> regions) {
        return discoverResources(connectionToken);
    }
}
