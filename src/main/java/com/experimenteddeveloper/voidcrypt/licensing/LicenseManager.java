package com.experimenteddeveloper.voidcrypt.licensing;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LicenseManager interface for managing VoidCrypt licensing operations.
 * Provides contracts for license validation, verification, and status checking.
 */
public interface LicenseManager {

    /**
     * Validates a license key against the licensing system.
     *
     * @param licenseKey the license key to validate
     * @return true if the license key is valid, false otherwise
     */
    boolean validateLicense(String licenseKey);

    /**
     * Verifies the authenticity of a license.
     *
     * @param licenseKey the license key to verify
     * @return true if the license is authentic, false otherwise
     */
    boolean verifyLicense(String licenseKey);

    /**
     * Retrieves license information for a given license key.
     *
     * @param licenseKey the license key to retrieve information for
     * @return an Optional containing the LicenseInfo if found, empty otherwise
     */
    Optional<LicenseInfo> getLicenseInfo(String licenseKey);

    /**
     * Checks if a license is currently active.
     *
     * @param licenseKey the license key to check
     * @return true if the license is active, false otherwise
     */
    boolean isLicenseActive(String licenseKey);

    /**
     * Checks if a license has expired.
     *
     * @param licenseKey the license key to check
     * @return true if the license has expired, false otherwise
     */
    boolean isLicenseExpired(String licenseKey);

    /**
     * Registers a new license in the system.
     *
     * @param licenseInfo the license information to register
     * @return true if registration was successful, false otherwise
     */
    boolean registerLicense(LicenseInfo licenseInfo);

    /**
     * Revokes a license from the system.
     *
     * @param licenseKey the license key to revoke
     * @return true if revocation was successful, false otherwise
     */
    boolean revokeLicense(String licenseKey);

    /**
     * Retrieves the remaining validity period of a license in days.
     *
     * @param licenseKey the license key to check
     * @return the number of days remaining, -1 if license not found or expired
     */
    int getRemainingValidityDays(String licenseKey);
}

/**
 * LicenseInfo class that holds information about a VoidCrypt license.
 * Contains details such as license key, expiration date, and license type.
 */
class LicenseInfo {

    private final String licenseKey;
    private final String licensee;
    private final LocalDateTime issuedDate;
    private final LocalDateTime expirationDate;
    private final String licenseType;
    private final boolean active;
    private final String productVersion;

    /**
     * Constructs a LicenseInfo object with the specified parameters.
     *
     * @param licenseKey the unique license key
     * @param licensee the name of the licensee
     * @param issuedDate the date the license was issued
     * @param expirationDate the date the license expires
     * @param licenseType the type/tier of the license
     * @param active whether the license is currently active
     * @param productVersion the product version this license covers
     */
    public LicenseInfo(String licenseKey, String licensee, LocalDateTime issuedDate,
                       LocalDateTime expirationDate, String licenseType, boolean active,
                       String productVersion) {
        this.licenseKey = licenseKey;
        this.licensee = licensee;
        this.issuedDate = issuedDate;
        this.expirationDate = expirationDate;
        this.licenseType = licenseType;
        this.active = active;
        this.productVersion = productVersion;
    }

    /**
     * Gets the license key.
     *
     * @return the license key
     */
    public String getLicenseKey() {
        return licenseKey;
    }

    /**
     * Gets the licensee name.
     *
     * @return the licensee name
     */
    public String getLicensee() {
        return licensee;
    }

    /**
     * Gets the issued date.
     *
     * @return the date the license was issued
     */
    public LocalDateTime getIssuedDate() {
        return issuedDate;
    }

    /**
     * Gets the expiration date.
     *
     * @return the date the license expires
     */
    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    /**
     * Gets the license type.
     *
     * @return the type/tier of the license
     */
    public String getLicenseType() {
        return licenseType;
    }

    /**
     * Checks if the license is active.
     *
     * @return true if the license is active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Gets the product version.
     *
     * @return the product version this license covers
     */
    public String getProductVersion() {
        return productVersion;
    }

    /**
     * Checks if the license has expired.
     *
     * @return true if the license has expired, false otherwise
     */
    public boolean hasExpired() {
        return LocalDateTime.now().isAfter(expirationDate);
    }

    /**
     * Gets the remaining validity period in days.
     *
     * @return the number of days remaining, negative if expired
     */
    public long getRemainingDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expirationDate);
    }

    @Override
    public String toString() {
        return "LicenseInfo{" +
                "licenseKey='" + licenseKey + '\'' +
                ", licensee='" + licensee + '\'' +
                ", issuedDate=" + issuedDate +
                ", expirationDate=" + expirationDate +
                ", licenseType='" + licenseType + '\'' +
                ", active=" + active +
                ", productVersion='" + productVersion + '\'' +
                '}';
    }
}
