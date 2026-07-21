package com.example.account.application.util;

/**
 * Masks a phone number for read responses: {@code 010-1234-5678 → 010-****-5678}.
 *
 * <p>Extracted from the identical {@code maskPhoneNumber} bodies previously inlined in
 * {@code AccountMeResult} and {@code ProfileUpdateResult}. (The divergent scheme in
 * {@code AccountDetailResponse.maskPhone} — mask-all-but-last-4, null on short input —
 * is deliberately a different masking and is not consolidated here.)
 */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    /**
     * Returns the phone number with its middle digits masked. Inputs that are
     * {@code null} or shorter than 4 characters are returned unchanged.
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return phoneNumber;
        }
        int len = phoneNumber.length();
        if (len <= 7) {
            return phoneNumber.substring(0, 3) + "-****";
        }
        String last4 = phoneNumber.substring(len - 4);
        String prefix = phoneNumber.substring(0, 3);
        return prefix + "-****-" + last4;
    }
}
