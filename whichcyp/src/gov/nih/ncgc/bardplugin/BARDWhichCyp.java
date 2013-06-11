package gov.nih.ncgc.bardplugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.NotFoundException;
import gov.nih.ncgc.bard.entity.Compound;
import gov.nih.ncgc.bard.entity.Substance;
import gov.nih.ncgc.bard.plugin.IPlugin;
import gov.nih.ncgc.bard.plugin.PluginManifest;
import gov.nih.ncgc.bard.rest.BadRequestException;
import gov.nih.ncgc.bard.tools.DBUtils;
import org.openscience.cdk.Molecule;
import org.openscience.cdk.MoleculeSet;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import whichcyp.WriteResultsAsChemDoodleHTML;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Rajarshi Guha
 */
@Path("/whichcyp")
public class BARDWhichCyp implements IPlugin {

    public BARDWhichCyp() {
    }

    @GET
    @Produces("text/plain")
    @Path("/_info")
    public String getDescription() {
        String msg = "WhichCyp is a method for prediction of which Cytochrome P450 isoform(s) is(are) likely to bind a drug-like molecule." +
                "\n\nThis plugin is a simple wrapper around the original code published by Rydberg and co-workers. See " +
                "http://130.225.252.198/whichcyp/about.php for more details and the original code or http://dx.doi.org/10.1093/bioinformatics/btt325" +
                " for the paper describing the work.\n" +
                "\nCurrently the plugin has two main resources: /summary and /. Each resource can take one of three query parameters, namely" +
                " smiles, cid or sid to indicate the calculation should be run on a SMILES string, a PubChem CID or a PubChem SID. Thus example" +
                " URLs would include /summary?smiles=CCNCC or /summary?cid=2242 etc. The resources also accept POST requests, where the names of" +
                " the form parameters are the same as the query parameters. In the POST versions, the form parameters should be comma separated lists" +
                " of SMILES, CIDs or SIDs." +
                "\n\nSee http://130.225.252.198/whichcyp/interpret.php?nomenu=1 " +
                "for how to interpret the results." +
                "\n\nThe JSON output is similar but not identical to the CSV output from the original tool. The response is a list of elements," +
                "where each element consists of two maps, labeled 'predictions' and 'sensitivityWarnings'. The former is a map keyed on isoform " +
                " identifier and the value is true if the molecule is predicted to bind to that isoform, false otherwise. The latter is also a " +
                " map keyed on isoform identifier and the value is true if the molecule is considered to be sufficiently dissimilar to the training" +
                " set for that isoforms' model.";

        return msg;
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Consumes("application/x-www-form-urlencoded")
    @Path("/summary")
    public Response getWhichCypSummaryByPost(@FormParam("smiles") String smiles,
                                             @FormParam("cid") String cid,
                                             @FormParam("sid") String sid) {
        try {
            if (smiles == null && cid == null && sid == null)
                throw new WebApplicationException(new Exception("Must specify form parameter: smiles, cid or sid"), 400);
            String page = null;

            if (smiles != null) page = getHtmlSummaryPage(smiles.split(","));
            else if (cid != null) {
                List<Long> cids = new ArrayList<Long>();
                for (String acid : cid.split(",")) cids.add(new Long(acid.trim()));
                DBUtils db = new DBUtils();
                List<Compound> cmpds = db.getCompoundsByCid(cids.toArray(new Long[0]));
                if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
                String[] csmiles = new String[cmpds.size()];
                for (int i = 0; i < cmpds.size(); i++) csmiles[i] = cmpds.get(i).getSmiles();
                page = getHtmlSummaryPage(csmiles);
                db.closeConnection();
            } else if (sid != null) {
                DBUtils db = new DBUtils();
                List<String> ssmiles = new ArrayList<String>();
                for (String asid : sid.split(",")) {
                    Substance subst = db.getSubstanceBySid(new Long(asid));
                    if (subst != null) ssmiles.add(subst.getSmiles());
                }
                if (ssmiles.size() == 0) throw new NotFoundException();
                page = getHtmlSummaryPage(ssmiles.toArray(new String[0]));
                db.closeConnection();
            }
            return Response.ok(page).type(MediaType.TEXT_HTML_TYPE).build();
        } catch (CDKException e) {
            throw new WebApplicationException(e, 500);
        } catch (CloneNotSupportedException e) {
            throw new WebApplicationException(e, 500);
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(e, 500);
        } catch (IOException e) {
            throw new WebApplicationException(e, 500);
        } catch (SQLException e) {
            throw new WebApplicationException(e, 500);
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(e, 500);
        }
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/summary")
    public Response getWhichCypSummary(@QueryParam("smiles") String smiles,
                                       @QueryParam("cid") Long cid,
                                       @QueryParam("sid") Long sid) {
        try {
            if (smiles == null && cid == null && sid == null)
                throw new WebApplicationException(new Exception("Must specify query parameter: smiles, cid or sid"), 400);
            String page = null;

            if (smiles != null) page = getHtmlSummaryPage(new String[]{smiles});
            else if (cid != null) {
                DBUtils db = new DBUtils();
                List<Compound> cmpds = db.getCompoundsByCid(cid);
                if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
                Compound cmpd = cmpds.get(0);
                page = getHtmlSummaryPage(new String[]{cmpd.getSmiles()});
                db.closeConnection();
            } else if (sid != null) {
                DBUtils db = new DBUtils();
                Substance subst = db.getSubstanceBySid(sid);
                if (subst == null) throw new NotFoundException();
                page = getHtmlSummaryPage(new String[]{subst.getSmiles()});
                db.closeConnection();
            }
            return Response.ok(page).type(MediaType.TEXT_HTML_TYPE).build();
        } catch (CDKException e) {
            throw new WebApplicationException(e, 500);
        } catch (CloneNotSupportedException e) {
            throw new WebApplicationException(e, 500);
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(e, 500);
        } catch (IOException e) {
            throw new WebApplicationException(e, 500);
        } catch (SQLException e) {
            throw new WebApplicationException(e, 500);
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(e, 500);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getWhichCypResults(@QueryParam("smiles") String smiles,
                                       @QueryParam("cid") Long cid,
                                       @QueryParam("sid") Long sid) throws IOException {

        if (smiles == null && cid == null && sid == null)
            throw new BadRequestException("Must specify query parameter: smiles, cid or sid");

        JsonNode data = null;
        try {
            if (smiles != null) {
                data = getResults(new String[]{smiles});
                ((ObjectNode) data.get(0)).put("id", (Integer) null);
            } else if (cid != null) {
                DBUtils db = new DBUtils();
                List<Compound> cmpds = db.getCompoundsByCid(cid);
                if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
                db.closeConnection();
                Compound cmpd = cmpds.get(0);
                data = getResults(new String[]{cmpd.getSmiles()});
                ((ObjectNode) data.get(0)).put("id", cid);
            } else if (sid != null) {
                DBUtils db = new DBUtils();
                Substance subst = db.getSubstanceBySid(sid);
                if (subst == null) throw new NotFoundException();
                db.closeConnection();
                data = getResults(new String[]{subst.getSmiles()});
                ((ObjectNode) data.get(0)).put("id", sid);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(e, 500);
        }
        ObjectMapper mapper = new ObjectMapper();
        return Response.ok(mapper.writeValueAsString(data)).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("application/x-www-form-urlencoded")
    @Path("/")
    public Response getWhichCypResultsByPost(@FormParam("smiles") String smiles,
                                             @FormParam("cid") String cid,
                                             @FormParam("sid") String sid) throws IOException {

        if (smiles == null && cid == null && sid == null)
            throw new BadRequestException("Must specify form parameter: smiles, cid or sid");

        JsonNode data = null;
        try {
            if (smiles != null) {
                data = getResults(smiles.split(","));
                for (int i = 0; i < smiles.split(",").length; i++) ((ObjectNode) data.get(i)).put("id", (Integer) null);
            } else if (cid != null) {
                List<Long> cids = new ArrayList<Long>();
                for (String acid : cid.split(",")) cids.add(new Long(acid.trim()));
                DBUtils db = new DBUtils();
                List<Compound> cmpds = db.getCompoundsByCid(cids.toArray(new Long[0]));
                if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
                db.closeConnection();
                List<String> csmiles = new ArrayList<String>();
                for (Compound cmpd : cmpds) csmiles.add(cmpd.getSmiles());
                data = getResults(csmiles.toArray(new String[0]));
                for (int i = 0; i < cmpds.size(); i++) ((ObjectNode) data.get(i)).put("id", cmpds.get(i).getCid());
            } else if (sid != null) {
                DBUtils db = new DBUtils();
                List<String> ssmiles = new ArrayList<String>();
                List<Substance> substs = new ArrayList<Substance>();
                for (String asid : sid.split(",")) {
                    Substance subst = db.getSubstanceBySid(new Long(asid.trim()));
                    if (subst != null) {
                        ssmiles.add(subst.getSmiles());
                        substs.add(subst);
                    }
                }
                if (ssmiles.size() == 0) throw new NotFoundException();
                for (int i = 0; i < substs.size(); i++) ((ObjectNode) data.get(i)).put("id", substs.get(i).getSid());
                db.closeConnection();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException(e, 500);
        }
        ObjectMapper mapper = new ObjectMapper();
        return Response.ok(mapper.writeValueAsString(data)).type(MediaType.APPLICATION_JSON).build();
    }

    JsonNode getResults(String[] smiles) throws Exception {
        WhichCypImpl WhichCypMain = new WhichCypImpl();
        MoleculeSet moleculeSet = WhichCypMain.readInStructures(smiles);
        Molecule molecule;
        int[][][] colorlabelatomset = new int[moleculeSet.getAtomContainerCount()][5][200];

        for (int moleculeIndex = 0; moleculeIndex < moleculeSet.getMoleculeCount(); moleculeIndex++) {
            molecule = (Molecule) moleculeSet.getMolecule(moleculeIndex);
            int[][] colorlabelatoms = WhichCypMain.predictIsoforms(molecule);
            colorlabelatomset[moleculeIndex] = colorlabelatoms;
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode molnodes = mapper.createArrayNode();
        for (IAtomContainer mol : moleculeSet.molecules()) {
            ObjectNode node = mapper.createObjectNode();
            ObjectNode bnode = mapper.createObjectNode();
            for (String isoform : new String[]{"1A2", "2C9", "2C19", "2D6", "3A4"})
                bnode.put(isoform, mol.getProperty("Binder" + isoform).equals(1));
            node.put("predictions", bnode);
            ObjectNode snode = mapper.createObjectNode();
            for (String isoform : new String[]{"1A2", "2C9", "2C19", "2D6", "3A4"})
                snode.put(isoform, mol.getProperty("SensitivityWarning" + isoform.toLowerCase()).equals(1));
            node.put("sensitivityWarnings", snode);
            molnodes.add(node);
        }
        return molnodes;
    }

    String getHtmlSummaryPage(String[] smiles) throws Exception {
        UUID uuid = UUID.randomUUID();
        String fname = uuid.toString();

        WhichCypImpl WhichCypMain = new WhichCypImpl();
        String dateAndTime = WhichCypMain.getDateAndTime();
        MoleculeSet moleculeSet = WhichCypMain.readInStructures(smiles);
        Molecule molecule;
        int[][][] colorlabelatomset = new int[moleculeSet.getAtomContainerCount()][5][200];

        for (int moleculeIndex = 0; moleculeIndex < moleculeSet.getMoleculeCount(); moleculeIndex++) {
            molecule = (Molecule) moleculeSet.getMolecule(moleculeIndex);
            int[][] colorlabelatoms = WhichCypMain.predictIsoforms(molecule);
            colorlabelatomset[moleculeIndex] = colorlabelatoms;
        }

        WriteResultsAsChemDoodleHTML writer = new WriteResultsAsChemDoodleHTML(dateAndTime, new String[]{}, "/tmp/", fname);
        writer.writeHTML(moleculeSet, colorlabelatomset);

        BufferedReader reader = new BufferedReader(new FileReader("/tmp/" + fname + ".html"));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        (new File(fname + ".html")).delete();
        return sb.toString();
    }


    @GET
    @Path("/_manifest")
    @Produces(MediaType.APPLICATION_JSON)
    public String getManifest() {
        PluginManifest pm = new PluginManifest();
        pm.setAuthor("Patrick Rydberg et al");
        pm.setAuthorEmail("pry@farma.ku.dk");
        pm.setMaintainer("Rajarshi Guha");
        pm.setMaintainerEmail("guhar@mail.nih.gov");
        pm.setTitle("WhichCyp");
        pm.setDescription("WhichCyp is a method for prediction of which Cytochrome P450 isoform(s) is(are) likely to bind a drug-like molecule. " +
                "See http://130.225.252.198/whichcyp/about.php for more details and the original code");
        pm.setVersion("1.0");

        PluginManifest.PluginResource res1 = new PluginManifest.PluginResource();
        res1.setPath("/summary");
        res1.setMimetype("text/html");
        res1.setMethod("GET");
        res1.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("smiles", "string", "query"),
                new PluginManifest.PathArg("cid", "number", "query"),
                new PluginManifest.PathArg("sid", "number", "query")
        });

        PluginManifest.PluginResource res2 = new PluginManifest.PluginResource();
        res1.setPath("/summary");
        res1.setMimetype("text/html");
        res1.setMethod("POST");
        res1.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("smiles", "string", "form"),
                new PluginManifest.PathArg("cid", "string", "form"),
                new PluginManifest.PathArg("sid", "string", "form")
        });


        PluginManifest.PluginResource res4 = new PluginManifest.PluginResource();
        res4.setPath("/");
        res4.setMimetype("application/json");
        res4.setMethod("GET");
        res4.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("smiles", "string", "query"),
                new PluginManifest.PathArg("cid", "number", "query"),
                new PluginManifest.PathArg("sid", "number", "query")
        });

        PluginManifest.PluginResource res3 = new PluginManifest.PluginResource();
        res4.setPath("/");
        res4.setMimetype("application/json");
        res4.setMethod("POST");
        res4.setArgs(new PluginManifest.PathArg[]{
                new PluginManifest.PathArg("smiles", "string", "form"),
                new PluginManifest.PathArg("cid", "string", "form"),
                new PluginManifest.PathArg("sid", "string", "form")
        });

        pm.setResources(new PluginManifest.PluginResource[]{res1, res2, res3, res4});

        return pm.toJson();
    }

}
