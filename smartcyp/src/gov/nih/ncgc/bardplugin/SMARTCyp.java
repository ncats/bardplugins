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
import gov.nih.ncgc.bard.tools.DBUtils;
import org.openscience.cdk.*;
import org.openscience.cdk.aromaticity.CDKHueckelAromaticityDetector;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.geometry.GeometryTools;
import org.openscience.cdk.geometry.Projector;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.interfaces.IMoleculeSet;
import org.openscience.cdk.io.MDLV2000Writer;
import org.openscience.cdk.io.listener.PropertiesListener;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.smiles.DeduceBondSystemTool;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import smartcyp.MoleculeKU;
import smartcyp.SMARTSnEnergiesTable;
import smartcyp.WriteResultsAsChemDoodleHTML;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Rajarshi Guha
 */
@Path("/smartcyp")
public class SMARTCyp implements IPlugin {

    public SMARTCyp() {
    }

    @GET
    @Produces("text/plain")
    @Path("/_info")
    public String getDescription() {
        String msg = "SMARTCyp is a method for prediction of which sites in a molecule that are most liable to metabolism by Cytochrome P450. " +
                "It has been shown to be applicable to metabolism by the isoforms 1A2, 2A6, 2B6, 2C8, 2C19, 2E1, and 3A4 (CYP3A4), and specific models" +
                " for the isoform 2C9 (CYP2C9) and isoform 2D6 (CYP2D6) are included from version 2.1. CYP3A4, CYP2D6, and CYP2C9 are " +
                "the three of the most important enzymes in drug metabolism since they are involved in the metabolism of more than half " +
                "of the drugs used today.\n\nThis plugin is a simple wrapper around the original code published by Rydberg and co-workers. See " +
                "http://www.farma.ku.dk/smartcyp/about.php for more details and the original code";
        return msg;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/{smiles}/summary")
    public Response getSmartCypSummary(@PathParam("smiles") String smiles) {
        try {
            String page = getHtmlSummaryPage(smiles);
            return Response.ok(page).type(MediaType.TEXT_HTML_TYPE).build();
        } catch (CDKException e) {
            throw new WebApplicationException(e, 500);
        } catch (CloneNotSupportedException e) {
            throw new WebApplicationException(e, 500);
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(e, 500);
        } catch (IOException e) {
            throw new WebApplicationException(e, 500);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{smiles}")
    public Response getSmartCypResults(@PathParam("smiles") String smiles) {
        try {
            JsonNode data = getResults(smiles);
            ObjectMapper mapper = new ObjectMapper();
            return Response.ok(mapper.writeValueAsString(data)).type(MediaType.APPLICATION_JSON).build();
        } catch (CDKException e) {
            throw new WebApplicationException(e, 500);
        } catch (CloneNotSupportedException e) {
            throw new WebApplicationException(e, 500);
        } catch (FileNotFoundException e) {
            throw new WebApplicationException(e, 500);
        } catch (IOException e) {
            throw new WebApplicationException(e, 500);
        }
    }
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/cid/{cid}")
    public Response getSmartCypResultsByCid(@PathParam("cid") Long cid) {
        try {
            DBUtils db = new DBUtils();
            List<Compound> cmpds = db.getCompoundsByCid(cid);
            if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
            db.closeConnection();
            Compound cmpd = cmpds.get(0);
            JsonNode data = getResults(cmpd.getSmiles());
            ObjectMapper mapper = new ObjectMapper();
            return Response.ok(mapper.writeValueAsString(data)).type(MediaType.APPLICATION_JSON).build();
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
        }
    }
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/sid/{sid}")
    public Response getSmartCypResultsBySid(@PathParam("sid") Long sid) {
        try {
            DBUtils db = new DBUtils();
            Substance subst = db.getSubstanceBySid(sid);
            if (subst == null) throw new NotFoundException();
            db.closeConnection();
            JsonNode data = getResults(subst.getSmiles());
            ObjectMapper mapper = new ObjectMapper();
            return Response.ok(mapper.writeValueAsString(data)).type(MediaType.APPLICATION_JSON).build();
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
        }
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/cid/{cid}/summary")
    public Response getSmartCypSummaryByCid(@PathParam("cid") Long cid) {
        try {
            DBUtils db = new DBUtils();
            List<Compound> cmpds = db.getCompoundsByCid(cid);
            if (cmpds == null || cmpds.size() == 0) throw new NotFoundException();
            Compound cmpd = cmpds.get(0);
            String page = getHtmlSummaryPage(cmpd.getSmiles());
            db.closeConnection();
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
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/sid/{sid}/summary")
    public Response getSmartCypSummaryBySid(@PathParam("sid") Long sid) {
        try {
            DBUtils db = new DBUtils();
            Substance subst = db.getSubstanceBySid(sid);
            if (subst == null) throw new NotFoundException();
            String page = getHtmlSummaryPage(subst.getSmiles());
            db.closeConnection();
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
        }
    }


    private JsonNode getResults(String smiles) throws CDKException, CloneNotSupportedException {
        ObjectMapper mapper = new ObjectMapper();

        IAtomContainer iAtomContainer = process(smiles);

        TreeSet<Atom> sortedAtomsTreeSet = ((MoleculeKU) iAtomContainer).getAtomsSortedByEnA();
        Iterator<Atom> sortedAtomsTreeSetIterator = sortedAtomsTreeSet.iterator();
        IAtom atom;

        ArrayNode anode3a4 = mapper.createArrayNode();
        while (sortedAtomsTreeSetIterator.hasNext()) {
            atom = sortedAtomsTreeSetIterator.next();
            int rank = MoleculeKU.SMARTCYP_PROPERTY.Ranking.get(atom).intValue();
            String id = atom.getSymbol() + "." + atom.getID();
            Number score = MoleculeKU.SMARTCYP_PROPERTY.Score.get(atom);
            Number energy = MoleculeKU.SMARTCYP_PROPERTY.Energy.get(atom);
            Number acc = MoleculeKU.SMARTCYP_PROPERTY.Accessibility.get(atom);
            Number sasa2d = MoleculeKU.SMARTCYP_PROPERTY.SASA2D.get(atom);

            ObjectNode node = mapper.createObjectNode();
            node.put("rank", rank);
            node.put("id", id);
            node.put("score", score.floatValue());
            node.put("energy", energy.floatValue());
            node.put("acc", acc.floatValue());
            node.put("sasa2d", sasa2d.floatValue());

            anode3a4.add(node);
        }

        sortedAtomsTreeSet = ((MoleculeKU) iAtomContainer).getAtomsSortedByEnA2D6();
        sortedAtomsTreeSetIterator = sortedAtomsTreeSet.iterator();
        ArrayNode anode2d6 = mapper.createArrayNode();
        while (sortedAtomsTreeSetIterator.hasNext()) {
            atom = sortedAtomsTreeSetIterator.next();
            int rank = MoleculeKU.SMARTCYP_PROPERTY.Ranking2D6.get(atom).intValue();
            String id = atom.getSymbol() + "." + atom.getID();
            Number score = MoleculeKU.SMARTCYP_PROPERTY.Score2D6.get(atom);
            Number energy = MoleculeKU.SMARTCYP_PROPERTY.Energy.get(atom);
            Number acc = MoleculeKU.SMARTCYP_PROPERTY.Accessibility.get(atom);
            Number sasa2d = MoleculeKU.SMARTCYP_PROPERTY.SASA2D.get(atom);

            ObjectNode node = mapper.createObjectNode();
            node.put("rank", rank);
            node.put("id", id);
            node.put("score", score.floatValue());
            node.put("energy", energy.floatValue());
            node.put("acc", acc.floatValue());
            node.put("sasa2d", sasa2d.floatValue());

            anode2d6.add(node);
        }

        sortedAtomsTreeSet = ((MoleculeKU) iAtomContainer).getAtomsSortedByEnA2C9();
        sortedAtomsTreeSetIterator = sortedAtomsTreeSet.iterator();
        ArrayNode anode2c9 = mapper.createArrayNode();
        while (sortedAtomsTreeSetIterator.hasNext()) {
            atom = sortedAtomsTreeSetIterator.next();
            int rank = MoleculeKU.SMARTCYP_PROPERTY.Ranking2C9.get(atom).intValue();
            String id = atom.getSymbol() + "." + atom.getID();
            Number score = MoleculeKU.SMARTCYP_PROPERTY.Score2C9.get(atom);
            Number energy = MoleculeKU.SMARTCYP_PROPERTY.Energy.get(atom);
            Number acc = MoleculeKU.SMARTCYP_PROPERTY.Accessibility.get(atom);
            Number sasa2d = MoleculeKU.SMARTCYP_PROPERTY.SASA2D.get(atom);

            ObjectNode node = mapper.createObjectNode();
            node.put("rank", rank);
            node.put("id", id);
            node.put("score", score.floatValue());
            node.put("energy", energy.floatValue());
            node.put("acc", acc.floatValue());
            node.put("sasa2d", sasa2d.floatValue());

            anode2c9.add(node);
        }

        // finally get the molecule with coords and atom ids
        iAtomContainer = generate2Dcoordinates((MoleculeKU) iAtomContainer);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MDLV2000Writer writer = new MDLV2000Writer(baos);
        Properties customSettings = new Properties();
        customSettings.setProperty("ForceWriteAs2DCoordinates", "true");
        PropertiesListener listener = new PropertiesListener(customSettings);
        writer.addChemObjectIOListener(listener);
        try {
            writer.write(iAtomContainer);
        } catch (CDKException e) {
        }
        String molstr = baos.toString();
//        molstr = molstr.replace("\\n", "\\\\n");
//        String[] mollines = molstr.split("\\r?\\n");


        ObjectNode root = mapper.createObjectNode();
        root.put("structure", molstr);
        root.put("3A4", anode3a4);
        root.put("2D6", anode2d6);
        root.put("2C9", anode2c9);

        return root;
    }

    MoleculeKU generate2Dcoordinates(MoleculeKU iAtomContainer) {
        final StructureDiagramGenerator structureDiagramGenerator = new StructureDiagramGenerator();
        if (GeometryTools.has2DCoordinates(iAtomContainer)) {
            return iAtomContainer; // already has 2D coordinates.
        } else {
            final AtomContainer iAtomContainer2d = new AtomContainer();
            synchronized (structureDiagramGenerator) {
                structureDiagramGenerator.setMolecule(iAtomContainer, true);
                structureDiagramGenerator.setUseTemplates(true);
                try {
                    structureDiagramGenerator.generateCoordinates();
                    iAtomContainer = (MoleculeKU) structureDiagramGenerator.getMolecule();
                } catch (final Exception e) {
                    Projector.project2D(iAtomContainer);
                }
            }
            if (GeometryTools.has2DCoordinates(iAtomContainer)) return iAtomContainer;
            else return null;
        }
    }

    String getHtmlSummaryPage(String smiles) throws CDKException, CloneNotSupportedException, IOException {
        UUID uuid = UUID.randomUUID();
        String fname = "/tmp/" + uuid.toString();
        IAtomContainer iAtomContainer = process(smiles);
        MoleculeSet mset = DefaultChemObjectBuilder.getInstance().newInstance(MoleculeSet.class);
        mset.addAtomContainer(iAtomContainer);
        WriteResultsAsChemDoodleHTML write = new WriteResultsAsChemDoodleHTML("", new String[]{}, "", fname);
        write.writeHTML(mset);

        BufferedReader reader = new BufferedReader(new FileReader(fname + ".html"));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        (new File(fname + ".html")).delete();
        return sb.toString();
    }


    private MoleculeKU process(String smiles) throws CDKException, CloneNotSupportedException {
        SMARTSnEnergiesTable SMARTSnEnergiesTable = new SMARTSnEnergiesTable();
        MoleculeKU moleculeKU = (MoleculeKU) getMolecule(smiles);
        moleculeKU.assignAtomEnergies(SMARTSnEnergiesTable.getSMARTSnEnergiesTable());
        moleculeKU.calculateDist2ProtAmine();
        moleculeKU.calculateDist2CarboxylicAcid();
        moleculeKU.calculateSpan2End();
        moleculeKU.unlikelyNoxidationCorrection();
        moleculeKU.calculateAtomAccessabilities();
        double[] SASA = moleculeKU.calculateSASA();
        moleculeKU.calculateAtomScores();
        moleculeKU.calculate2D6AtomScores();
        moleculeKU.calculate2C9AtomScores();
        moleculeKU.sortAtoms();
        moleculeKU.rankAtoms();
        moleculeKU.sortAtoms2D6();
        moleculeKU.rankAtoms2D6();
        moleculeKU.sortAtoms2C9();
        moleculeKU.rankAtoms2C9();
        return moleculeKU;
    }

    private IAtomContainer getMolecule(String smiles) throws CDKException, CloneNotSupportedException {
        int highestMoleculeID = 1;
        SMARTSnEnergiesTable energyTable = new SMARTSnEnergiesTable();

        SmilesParser sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        IMolecule mol = sp.parseSmiles(smiles);

        CDKHydrogenAdder adder = CDKHydrogenAdder.getInstance(DefaultChemObjectBuilder.getInstance());

        if (!ConnectivityChecker.isConnected(mol)) {
            IMoleculeSet fragments = ConnectivityChecker.partitionIntoMolecules(mol);
            int maxID = 0;
            int maxVal = -1;
            for (int i = 0; i < fragments.getMoleculeCount(); i++) {
                if (fragments.getMolecule(i).getAtomCount() > maxVal) {
                    maxID = i;
                    maxVal = fragments.getMolecule(i).getAtomCount();
                }
            }
            mol = fragments.getMolecule(maxID);
        }
        IAtomContainer iAtomContainer = AtomContainerManipulator.removeHydrogens(mol);
        AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(iAtomContainer);
        DeduceBondSystemTool dbst = new DeduceBondSystemTool();
        iAtomContainer = dbst.fixAromaticBondOrders((IMolecule) iAtomContainer);
        adder.addImplicitHydrogens(iAtomContainer);
        CDKHueckelAromaticityDetector.detectAromaticity(iAtomContainer);
        IAtomContainer moleculeKU = new MoleculeKU(iAtomContainer, energyTable.getSMARTSnEnergiesTable());
        moleculeKU.setID(Integer.toString(highestMoleculeID));
        moleculeKU.setProperty(CDKConstants.TITLE, mol.getProperty(CDKConstants.TITLE));
        return moleculeKU;
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
        pm.setTitle("SMARTCyp");
        pm.setDescription("SMARTCyp is a method for prediction of which sites in a molecule that are most liable to metabolism by Cytochrome P450. See http://www.farma.ku.dk/smartcyp/about.php for more details and the original code");
        pm.setVersion("1.1");

        PluginManifest.PluginResource res1 = new PluginManifest.PluginResource();
        res1.setPath("/{smiles}/summary");
        res1.setMimetype("text/html");
        res1.setMethod("GET");
        res1.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("smiles", "string")});

        PluginManifest.PluginResource res2 = new PluginManifest.PluginResource();
        res2.setPath("/cid/{cid}/summary");
        res2.setMimetype("text/html");
        res2.setMethod("GET");
        res2.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("cid", "number")});

        PluginManifest.PluginResource res3 = new PluginManifest.PluginResource();
        res3.setPath("/sid/{sid}/summary");
        res3.setMimetype("text/html");
        res3.setMethod("GET");
        res3.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("sid", "number")});

        PluginManifest.PluginResource res4 = new PluginManifest.PluginResource();
        res4.setPath("/{smiles}");
        res4.setMimetype("application/json");
        res4.setMethod("GET");
        res4.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("smiles", "string")});

        PluginManifest.PluginResource res5 = new PluginManifest.PluginResource();
        res5.setPath("/cid/{cid}");
        res5.setMimetype("application/json");
        res5.setMethod("GET");
        res5.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("cid", "number")});

        PluginManifest.PluginResource res6 = new PluginManifest.PluginResource();
        res6.setPath("/sid/{sid}");
        res6.setMimetype("application/json");
        res6.setMethod("GET");
        res6.setArgs(new PluginManifest.PathArg[]{new PluginManifest.PathArg("sid", "number")});


        pm.setResources(new PluginManifest.PluginResource[]{res1, res2, res3, res4, res5, res6});

        return pm.toJson();
    }

    public static void main(String[] args) {
        SMARTCyp s = new SMARTCyp();
        s.getSmartCypSummary("CCCCNCCCC");
    }
}
