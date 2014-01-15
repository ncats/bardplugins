package gov.nih.ncgc.bardplugin;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import gov.nih.ncgc.bard.plugin.IPlugin;
import gov.nih.ncgc.bard.plugin.PluginManifest;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Rajarshi Guha
 */
@Path("/initdemo")
public class InitDemo implements IPlugin, ServletContextListener {
    private static int servletInitCounter = 0;
    private static int ctorInitCounter = 0;

    public InitDemo() {
        System.out.println("InitDemo ctor called");
        ctorInitCounter++;
    }

    @GET
    @Produces("text/plain")
    @Path("/_info")
    public String getDescription() {
        return "Demo plugin";
    }


    @GET
    @Produces("text/plain")
    @Path("/res")
    public Response get() {
        try {
            Molecule molecule = MolImporter.importMol("C1=CC=CC=C1");
            System.out.println(molecule);
        } catch (MolFormatException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return Response.ok("Calling this URL multiple times should show ctorInitCounter increasing, but" +
                " servletInitCount remaining at 1.\n" +
                "servletInitCounter " + servletInitCounter + " ctorInitCounter " + ctorInitCounter,
                MediaType.TEXT_PLAIN_TYPE).build();
    }


    @GET
    @Path("/_manifest")
    @Produces(MediaType.APPLICATION_JSON)
    public String getManifest() {
        PluginManifest pm = new PluginManifest();
        pm.setAuthor("Rajarshi Guha");
        pm.setAuthorEmail("guhar@mail.nih.gov");
        pm.setMaintainer("Rajarshi Guha");
        pm.setMaintainerEmail("guhar@mail.nih.gov");
        pm.setTitle("InitDemo");
        pm.setDescription("Test plugin initialization");
        pm.setVersion("1.0");

        PluginManifest.PluginResource res1 = new PluginManifest.PluginResource();
        res1.setPath("/res");
        res1.setMimetype("text/plain");
        res1.setMethod("GET");

        pm.setResources(new PluginManifest.PluginResource[]{res1});

        return pm.toJson();
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {

        // accessing parameters from web.xml only works in this method if they
        // are context params (webapp scope) and not if they are init-params (servlet scope)
        String initParam = servletContextEvent.getServletContext().getInitParameter("aInitParam");
        String contextParam = servletContextEvent.getServletContext().getInitParameter("aContextParam");
        System.out.println("contextParam = " + contextParam);
        System.out.println("initParam = " + initParam);
        System.out.println("contextInitialized() was called");
        servletInitCounter++;
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        System.out.println("contextDestroyed() called");
    }
}
