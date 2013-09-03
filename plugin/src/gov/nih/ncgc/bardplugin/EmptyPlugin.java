package gov.nih.ncgc.bardplugin;

import gov.nih.ncgc.bard.plugin.IPlugin;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URISyntaxException;


/**
 * This plugin handles requests for the root of the plugin hierarchy.
 *
 * As a result the plugin is not really meant for client usage and instead
 * simply handles requests to /plugins (ie when no specific plugin is requested).
 * Given the limited scope of this plugin, it does not conform fully to the
 * plugin specification and will fail the validation tests.
 *
 * @author Rajarshi Guha
 */
@Path("/plugin")
public class EmptyPlugin implements IPlugin {
    private static final String VERSION = "0.0";

    @GET
    @Produces("text/plain")
    @Path("/_info")
    public String getDescription() {
        return "This is the root of the plugin resource hierarchy. To get a list of useful plugins visit /plugins/registry/list";
    }

    public String getManifest() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


    @GET
    @Path("/")
    public Response redirectToAPIDocs() throws URISyntaxException {
        return Response.ok("<html><head>" +
                "<style type=\"text/css\"> body { margin: 5em; } p { font-family: sans-serif; font-size: x-large; }</style></head>" +
                "<body>" +
                "<p>Nothing to see here. You may be looking for the <a href='/api/latest/plugins/registry/list'>plugin registry</a> " +
                "or the <a href='https://github.com/ncatsdpiprobedev/bard/wiki'>development wiki</a>.</p>" +
                "</body></html>", MediaType.TEXT_HTML_TYPE).status(404).build();
    }
}
