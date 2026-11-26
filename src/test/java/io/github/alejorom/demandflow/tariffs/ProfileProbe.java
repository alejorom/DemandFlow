package io.github.alejorom.demandflow.tariffs;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Component;

/** Test-only component proving that future module components cannot bypass profile-scoped scanning. */
@Component("catalogProfileProbe")
public class ProfileProbe {
    public ProfileProbe(EntityManagerFactory factory) { }
}
