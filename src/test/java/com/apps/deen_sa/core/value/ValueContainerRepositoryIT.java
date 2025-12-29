package com.apps.deen_sa.core.value;

import com.apps.deen_sa.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class ValueContainerRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ValueContainerRepo repo;

    @Test
    void contextLoads_andDatabaseWorks() {
        assertNotNull(repo);

        ValueContainerEntity vc = new ValueContainerEntity();
        vc.setOwnerType("USER");
        vc.setOwnerId(123L);
        vc.setContainerType("CASH");
        vc.setName("Test Container");
        vc.setStatus("ACTIVE");

        ValueContainerEntity saved = repo.save(vc);
        assertNotNull(saved.getId());

        var fetched = repo.findById(saved.getId());
        assertTrue(fetched.isPresent());
        assertEquals("Test Container", fetched.get().getName());
    }
}
