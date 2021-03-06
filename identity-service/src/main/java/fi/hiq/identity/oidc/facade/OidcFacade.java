package fi.hiq.identity.oidc.facade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import fi.hiq.identity.oidc.domain.AuthorizationCodeHandler;
import fi.hiq.identity.oidc.domain.AuthorizationRequestHandler;
import fi.hiq.identity.oidc.domain.OidcClientConfiguration;
import fi.hiq.identity.oidc.domain.IdTokenHandler;
import fi.hiq.identity.oidc.domain.JwksLoader;
import fi.hiq.identity.oidc.domain.KeystoreLoader;
import fi.hiq.identity.oidc.domain.OidcKey;
import fi.hiq.identity.oidc.domain.OidcRequestParameters;
import fi.hiq.identity.oidc.domain.OidcResponseParameters;
import fi.hiq.identity.oidc.dto.IdentityResponseDTO;
import fi.hiq.identity.oidc.exceptions.CommonOidcException;

public class OidcFacade {

    private final Logger logger = LoggerFactory.getLogger(OidcFacade.class);

    // Stores private encryption key and private signing key
    private KeystoreLoader keyLoader;
    // Retrieves identity broker's public keys
    private JwksLoader jwksLoader;
    // Creates OIDC authorization request
    private AuthorizationRequestHandler authorizationRequestHandler;
    // Exchanges authorization code to get identity token
    private AuthorizationCodeHandler authorizationCodeHandler;
    // Decrypts identity token
    private IdTokenHandler idTokenHandler;

    @Autowired
    public OidcFacade() {
        String location = OidcClientConfiguration.KEYSTORE_LOCATION;
        keyLoader = new KeystoreLoader(location);
        jwksLoader = new JwksLoader();
        authorizationRequestHandler = new AuthorizationRequestHandler(keyLoader);
        authorizationCodeHandler = new AuthorizationCodeHandler(keyLoader);
        idTokenHandler = new IdTokenHandler();
    }

    public IdentityResponseDTO extractIdentity(OidcResponseParameters response, OidcRequestParameters requestData) {
        try {
            jwksLoader.setJwksProxy(OidcClientConfiguration.JWKS_PROXY);
            String encryptedIdToken = this.getIdToken(response, requestData);
            return idTokenHandler.extractIdentity(encryptedIdToken, keyLoader, jwksLoader);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CommonOidcException("Error extracting identity!", e);
        }
    }
    
    private String getIdToken(OidcResponseParameters response, OidcRequestParameters requestData) {
        try {
            String idToken = authorizationCodeHandler.exchangeCodeForIdToken(response.getCode(), requestData);
            logger.info("Encrypted ID Token: {}", idToken);
            return idToken; 
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CommonOidcException("Error extracting decrypted ID token!", e);
        }
    }

    OidcRequestParameters readFromContract(String idp, String lang, String purpose) {
        OidcRequestParameters result = new OidcRequestParameters();

        // Identity broker specific settings
        result.setClientId(OidcClientConfiguration.CLIENT_ID);
        result.setResponseType(OidcClientConfiguration.RESPONSE_TYPE);
        result.setEndpointUrl(OidcClientConfiguration.AUTHORIZE_URL);
        result.setJwksProxy(OidcClientConfiguration.JWKS_PROXY);
        result.setTokenUrl(OidcClientConfiguration.TOKEN_URL);
        result.setTokenProxy(OidcClientConfiguration.TOKEN_PROXY);
        result.setPrompt(OidcClientConfiguration.PROMPT);
        result.setRedirectUri(OidcClientConfiguration.REDIRECT_URI);

        // Identity provider specific settings
        result.setScope(OidcClientConfiguration.SCOPE);
        result.setUiLocales(lang);
        result.setFtnIdpId(idp);
        result.setScope(getScope());
        return result;
    }

    private String getScope() {
        String commonScope = "openid profile personal_identity_code";
        String extraScope = " strong";
        return commonScope + extraScope;
    }

    // Creates OIDC authorization request.
    public OidcRequestParameters oidcAuthMessage(String idp, String language, String requestId, boolean doPrompt, String purpose) {

        OidcRequestParameters signedResponse = readFromContract(idp, language, purpose);

        signedResponse.setNonce(UUID.randomUUID().toString());
        signedResponse.setState(requestId);

        signedResponse.setNonce(signedResponse.getNonce().replaceAll("-", ""));
        signedResponse.setState(signedResponse.getState().replaceAll("-", ""));

        if (doPrompt) {
            signedResponse.setPrompt("consent");
        }

        try {
            authorizationRequestHandler.sign(signedResponse);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CommonOidcException("Signing authorization request failed!", e);
        }
        logger.info("Signed response: {}", signedResponse);
        return signedResponse;
    }

    // Returns user's public keys as Java Web Key Set.
    public String getJwks() {
        List<JWK> webKeys = new ArrayList<>();

        for (OidcKey key : keyLoader.getKeys()) {
            boolean isSigningKey = OidcKey.USE_SIGNING.equals(key.getUse());
            KeyUse keyUse = isSigningKey ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION;

            if (key.getShowInJwks()) {
                RSAKey jwkKey = new RSAKey.Builder(key.getPublicKey()) //
                        .keyID(key.getKeyId()) //
                        .keyUse(keyUse) //
                        .build();
                webKeys.add(jwkKey);
            }
        }

        JWKSet set = new JWKSet(webKeys);
        return set.toJSONObject().toString();
    }
}
