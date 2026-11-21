package com.cmacgm.gbs.rst.api.security;

import org.springframework.http.HttpStatus;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Source Data scope. A Local Transformation Head configures only the identity
 * Center. Global files and settings stay with ADMIN, including an account that
 * also has the LTH role.
 */
public final class SourceDataAccess {

    private SourceDataAccess() {
    }

    /**
     * @param principal caller
     * @return true when the caller is an LTH and must stay inside one Center
     */
    public static boolean centerScoped(RstPrincipal principal) {
        return principal != null
                && principal.roles() != null
                && principal.roles().contains(RstRoles.LOCAL_TRANSFORMATION_HEAD);
    }

    /**
     * Rejects an LTH. Global source settings and multi-center files are ADMIN only.
     *
     * @param principal caller
     */
    public static void requireGlobal(RstPrincipal principal) {
        if (centerScoped(principal)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "source-global-forbidden",
                    "Global Source Data settings are limited to ADMIN.");
        }
    }

    /**
     * @param principal caller
     * @return canonical identity Center
     */
    public static String ownCenter(RstPrincipal principal) {
        String center = RstCenters.canonicalize(principal == null ? null : principal.center());
        if (center == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "identity-center-missing",
                    "Current identity has no Center.");
        }
        return center;
    }

    /**
     * Keeps an ADMIN filter unchanged. An LTH is fixed to the identity Center.
     *
     * @param principal caller
     * @param requested requested center, blank to use the identity Center
     * @return center the caller may read
     */
    public static String visibleCenter(RstPrincipal principal, String requested) {
        if (!centerScoped(principal)) {
            return requested;
        }
        String own = ownCenter(principal);
        String asked = RstCenters.canonicalize(requested);
        if (asked != null && !own.equals(asked)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "source-center-forbidden",
                    "Source Data is limited to Center " + own + ".");
        }
        return own;
    }

    /**
     * An LTH may open a Daily or Monthly file for the identity Center only.
     * MyHR and SKPI files cover every MyHR center and stay with ADMIN.
     *
     * @param principal caller
     * @param kind snapshot kind
     * @param center snapshot center
     */
    public static void requireOwnSnapshot(RstPrincipal principal, String kind, String center) {
        if (!centerScoped(principal)) {
            return;
        }
        if ("MYHR".equals(kind) || "SHARED_KPI".equals(kind)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "source-global-forbidden",
                    "MyHR and SKPI files cover more than one Center.");
        }
        String own = ownCenter(principal);
        String fileCenter = RstCenters.canonicalize(center);
        if (fileCenter == null || !own.equals(fileCenter)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "source-center-forbidden",
                    "Source Data is limited to Center " + own + ".");
        }
    }
}
