package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.finance.account.ValueContainerCache;
import com.apps.deen_sa.core.value.ValueContainerEntity;
import com.apps.deen_sa.core.value.ValueContainerRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValueContainerService {

    private final ValueContainerRepo repository;
    private final ValueContainerCache cache;

    public ValueContainerService(ValueContainerRepo repository,
                                 ValueContainerCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public ValueContainerEntity findValueContainerById (Long valueId) {
        return repository.findById(valueId)
                .orElseThrow(() ->
                        new IllegalStateException("Source container not found"));
    }

    public void UpdateValueContainer (ValueContainerEntity entity) {
        repository.save(entity);
    }

    public List<ValueContainerEntity> getActiveContainers(Long ownerId) {

        // 1️⃣ Try cache
        List<ValueContainerEntity> cached = cache.getActiveContainers(ownerId);
        if (cached != null) {
            return cached;
        }

        // 2️⃣ Hit DB
        List<ValueContainerEntity> containers =
                repository.findActiveByOwnerId(ownerId);

        // 3️⃣ Populate cache
        cache.putActiveContainers(ownerId, containers);

        return containers;
    }

    // Call this after ANY container update
    public void evictCache(Long ownerId) {
        cache.evict(ownerId);
    }
}
