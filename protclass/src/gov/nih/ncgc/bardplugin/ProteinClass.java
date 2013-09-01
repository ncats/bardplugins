package gov.nih.ncgc.bardplugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.NotFoundException;
import gov.nih.ncgc.bard.entity.ProteinTarget;
import gov.nih.ncgc.bard.entity.TargetClassification;
import gov.nih.ncgc.bard.plugin.IPlugin;
import gov.nih.ncgc.bard.plugin.PluginManifest;
import gov.nih.ncgc.bard.tools.DBUtils;
import gov.nih.ncgc.bard.tools.Util;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Rajarshi Guha
 */
@Path("/protclass")
public class ProteinClass implements IPlugin {

    public ProteinClass() {
    }

    @GET
    @Produces("text/plain")
    @Path("/_info")
    public String getDescription() {
        return "This plugin returns pre-computed protein classifications based on Uniprot IDs. Currently, " +
                "only the Panther classification system is provided, though others may become available in " +
                "the future";
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{source}/{id}")
    public Response getClassification(@PathParam("source") String source, @PathParam("id") String id) throws SQLException, IOException {

        if (id == null)
            throw new WebApplicationException(new Exception("Must specify a Uniprot ID"), 400);
        List<TargetClassification> classes = null;
        DBUtils db = new DBUtils();
        if (source.toLowerCase().equals("panther")) {
            classes = db.getPantherClassesForAccession(id);
        }
        db.closeConnection();
        if (classes == null)
            throw new NotFoundException("No classifications for " + id + " in the " + source + " hierarchy");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        JsonNode classNodes = mapper.valueToTree(classes);
        node.put(id, classNodes);
        String json = mapper.writeValueAsString(node);
        return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @POST
    @Path("/{source}")
    @Consumes("application/x-www-form-urlencoded")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClassificationsForAccessions(@PathParam("source") String source,
                                                    @FormParam("ids") String accs)
            throws SQLException, IOException {

        DBUtils db = new DBUtils();
        String[] laccs = accs.split(",");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();

        for (String acc : laccs) {
            List<TargetClassification> classes = null;
            if (source.toLowerCase().equals("panther")) {
                classes = db.getPantherClassesForAccession(acc.trim());
            }
            JsonNode classNodes = mapper.valueToTree(classes);
            node.put(acc, classNodes);
        }
        db.closeConnection();
        String json = mapper.writeValueAsString(node);
        return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @GET
    @Path("/classification/{source}/{clsid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccessionsForClassification(@PathParam("source") String source,
                                                   @PathParam("clsid") String clsid) 
            throws SQLException, IOException {
        List<ProteinTarget> targets = null;
        DBUtils db = new DBUtils();
        if (source.toLowerCase().equals("panther")) {
            targets = db.getProteinTargetsForPantherClassification(clsid);
        }
        db.closeConnection();
        if (targets == null)
            throw new NotFoundException("No protein targets for " + clsid + " in the " + source + " hierarchy");
        return Response.ok(Util.toJson(targets)).type(MediaType.APPLICATION_JSON_TYPE).build();
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
        pm.setTitle("ProtClass");
        pm.setDescription("Obtain protein class according to different classification schemes");
        pm.setVersion("1.0");

        PluginManifest.PluginResource res1 = new PluginManifest.PluginResource();
        res1.setPath("/{source}/{id}");
        res1.setMimetype("application/json");
        res1.setMethod("GET");
        res1.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("source", "string", "path"),
                new PluginManifest.PathArg("id", "string", "path"),
        });

        PluginManifest.PluginResource res2 = new PluginManifest.PluginResource();
        res2.setPath("/{source}");
        res2.setMimetype("application/json");
        res2.setMethod("POST");
        res2.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("source", "string", "path"),
                new PluginManifest.PathArg("ids", "string", "form"),
        });

        PluginManifest.PluginResource res3 = new PluginManifest.PluginResource();
        res3.setPath("/{source}");
        res3.setMimetype("application/json");
        res3.setMethod("POST");
        res3.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("source", "string", "path"),
                new PluginManifest.PathArg("clsid", "string", "path"),
        });

        pm.setResources(new PluginManifest.PluginResource[]{res1, res2, res3});

        return pm.toJson();
    }

}
