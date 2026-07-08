package com.petspark.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenFamilyRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeInIndependentTransaction(String familyId) {
        repository.revokeFamily(familyId);
    }
}
