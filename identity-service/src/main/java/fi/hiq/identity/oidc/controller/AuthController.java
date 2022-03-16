package fi.hiq.identity.oidc.controller;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import fi.hiq.identity.oidc.domain.OidcRequestParameters;
import fi.hiq.identity.oidc.domain.OidcResponseParameters;
import fi.hiq.identity.oidc.dto.AuthResponseDTO;
import fi.hiq.identity.oidc.dto.IdentityResponseDTO;
import fi.hiq.identity.oidc.exceptions.IllegalParameterException;
import fi.hiq.identity.oidc.facade.OidcFacade;

@RestController
public class AuthController {
    
	private final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private OidcFacade facade;

    @RequestMapping(value="/authorize", method = RequestMethod.GET)
    public ResponseEntity<AuthResponseDTO> initFlow(HttpServletRequest request) {

        String language = "fi";
        String idp = request.getParameter("idp");
        String requestId = UUID.randomUUID().toString();
        String promptParam = "consent";
        String purpose = "strong";

        boolean prompt = promptParam != null && promptParam.equals("consent");
        OidcRequestParameters params = getFacade().oidcAuthMessage(idp, language, requestId, prompt, purpose);
        logger.info("Auth Request: {}", params.getRequest());
        
        request.getSession().setAttribute("initParams", params);
        return ResponseEntity.ok(new AuthResponseDTO(params.getEndpointUrl() + "?request=" + params.getRequest(), params.getRequest()));
    }

    @RequestMapping(value="/token", method = RequestMethod.GET)
    public ResponseEntity<IdentityResponseDTO> finishFlow(HttpServletRequest request) {
    	validateParams(request);
        
        OidcRequestParameters originalParams = (OidcRequestParameters) request.getSession().getAttribute("initParams");
        validateState(request, originalParams);
        
        OidcResponseParameters response = new OidcResponseParameters();
        response.setState(request.getParameter("state"));
        response.setCode(request.getParameter("code"));
        IdentityResponseDTO identity = getFacade().extractIdentity(response, originalParams);
        
        return ResponseEntity.ok(identity);
    }

    private void validateParams(HttpServletRequest request) {
		if (request.getParameter("code") == null) {
			throw new IllegalParameterException("Request missing code");
		}
	}
    
	private void validateState(HttpServletRequest request, OidcRequestParameters originalParams) {
		if (originalParams.getState() == null || 
        	originalParams.getState().length() == 0 ||
        	request.getParameter("state") == null || 
        	request.getParameter("state").length() == 0) {
        	throw new IllegalParameterException("Request missing state");
        } else if (!originalParams.getState().equals(request.getParameter("state"))) {
        	throw new IllegalParameterException("Invalid state");
        }
	}

    @RequestMapping(value = "/jwks", method = RequestMethod.GET)
    @ResponseBody
    public String jwks() {
        return getFacade().getJwks();
    }

    private OidcFacade getFacade() {
        if (facade == null) {
            facade = new OidcFacade();
        }
        return facade;
    }
}
