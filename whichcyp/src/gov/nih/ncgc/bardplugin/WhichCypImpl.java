package gov.nih.ncgc.bardplugin;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.Molecule;
import org.openscience.cdk.MoleculeSet;
import org.openscience.cdk.aromaticity.CDKHueckelAromaticityDetector;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.io.ISimpleChemObjectReader;
import org.openscience.cdk.smiles.DeduceBondSystemTool;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import whichcyp.WhichCyp;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Rajarshi Guha
 */
public class WhichCypImpl {

    protected String getDateAndTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    protected MoleculeSet readInStructures(String[] smiles) throws CloneNotSupportedException, CDKException {
        MoleculeSet moleculeSet = new MoleculeSet();
        IChemObjectBuilder builder = DefaultChemObjectBuilder.getInstance();
        SmilesParser sp = new SmilesParser(builder);
        ISimpleChemObjectReader reader;

        for (String aSmiles : smiles) {
            IAtomContainer mol = sp.parseSmiles(aSmiles.trim());
            CDKHydrogenAdder adder = CDKHydrogenAdder.getInstance(DefaultChemObjectBuilder.getInstance());
            mol = AtomContainerManipulator.removeHydrogens(mol);
            Molecule molecule;
            if (mol.getAtomCount() > 1) {
                AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
                DeduceBondSystemTool dbst = new DeduceBondSystemTool();
                mol = dbst.fixAromaticBondOrders((IMolecule) mol);
                adder.addImplicitHydrogens(mol);
                CDKHueckelAromaticityDetector.detectAromaticity(mol);
                molecule = new Molecule(mol);
                moleculeSet.addMolecule(molecule);
            }
        }
        return moleculeSet;
    }

    public static int[][] predictIsoforms(Molecule molecule) throws Exception {

        int startHeight = 0;
        int endHeight = 3;
        String[] isoforms = {"1a2", "2c9", "2c19", "2d6", "3a4"};
        int[] SensitivityWarningLimits = {21, 14, 18, 3, 18};  //set to maximum number of allowed missing signatures of height 3 for which the test set gives a sensitivity > 0.6
        int[][] colorlabels = new int[isoforms.length][molecule.getAtomCount()];

        int[] MissingSignList = new int[5];
        for (int iso = 0; iso < isoforms.length; iso++) {

            String modelPath = "whichcyp/selectivityfiles/" + isoforms[iso] + ".svm";
            String signaturesPath = "whichcyp/selectivityfiles/" + isoforms[iso] + "-sign.txt";

            //read in the signatures that were used to create the model
            List<String> signatures;
            signatures = readSignaturesFile(signaturesPath);

            //generate signatures for the molecule
            Map<String, Double> moleculeSignatures = new HashMap<String, Double>(); // Contains the signatures for a molecule and the count. We store the count as a double although it is an integer. libsvm wants a double.
            Map<String, Integer> moleculeSignaturesHeight = new HashMap<String, Integer>(); //Contains the height for a specific signature.
            Map<String, List<Integer>> moleculeSignaturesAtomNr = new HashMap<String, List<Integer>>(); //Contains the atomNr for a specific signature.

            for (int height = startHeight; height <= endHeight; height++) {
                List<String> signs;

                signs = WhichCyp.generateSignatures(molecule, height).getSignatures();

                Iterator<String> signsIter = signs.iterator();
                int signsIndex = 0;
                int signMissed = 0;
                while (signsIter.hasNext()) {
                    String currentSignature = signsIter.next();
                    if (signatures.contains(currentSignature)) {
                        if (!moleculeSignaturesAtomNr.containsKey(currentSignature)) {
                            moleculeSignaturesAtomNr.put(currentSignature, new ArrayList<Integer>());
                        }
                        moleculeSignaturesHeight.put(currentSignature, height);
                        List<Integer> tmpList = moleculeSignaturesAtomNr.get(currentSignature);
                        tmpList.add(signsIndex);
                        moleculeSignaturesAtomNr.put(currentSignature, tmpList);
                        if (moleculeSignatures.containsKey(currentSignature)) {
                            moleculeSignatures.put(currentSignature, (Double) moleculeSignatures.get(currentSignature) + 1.00);
                        } else {
                            moleculeSignatures.put(currentSignature, 1.0);
                        }
                    } else {
                        signMissed++;
                    }
                    signsIndex++;
                }

                if (height == 3) {
                    MissingSignList[iso] = signMissed;
                }
            }


            molecule.setProperty("MissingSignatures" + isoforms[iso], MissingSignList[iso]);

            //determine if a sensitivitywarning should be issued
            if (MissingSignList[iso] > SensitivityWarningLimits[iso]) {
                molecule.setProperty("SensitivityWarning" + isoforms[iso], 1);
            } else molecule.setProperty("SensitivityWarning" + isoforms[iso], 0);

            //load the svm model
            svm_model svmModel;
            try {
                InputStream modelstream = WhichCyp.class.getClassLoader().getResourceAsStream(modelPath);
                svmModel = svm.svm_load_model(new BufferedReader(new InputStreamReader(modelstream)));
                //svmModel = svm.svm_load_model(modelPath);
            } catch (IOException e) {
                throw new Exception("Could not read model file '" + modelPath + "' due to: " + e.getMessage());
            }

            svm_node[] moleculeArray = new svm_node[moleculeSignatures.size()];

            //add signatures to moleculeArray
            Iterator<String> signaturesIter = signatures.iterator();
            int i = 0;
            while (signaturesIter.hasNext()) {
                String currentSignature = signaturesIter.next();
                if (moleculeSignatures.containsKey(currentSignature)) {
                    moleculeArray[i] = new svm_node();
                    moleculeArray[i].index = signatures.indexOf(currentSignature) + 1; // libsvm assumes that the index starts at one.
                    moleculeArray[i].value = (Double) moleculeSignatures.get(currentSignature);
                    i = i + 1;
                }
            }
            //run the prediction
            double prediction = 0.0;
            prediction = svm.svm_predict(svmModel, moleculeArray); //1.0 is non-binder, 2.0 is binder
            //System.out.println("Prediction: " + prediction + " isoform " + iso);

            //set molecular properties for binder non/binder
            int binds = 1;
            int dontbinds = 0;

            if (prediction == 2.0 && iso == 0) {
                molecule.setProperty("Binder1A2", binds);
            } else if (iso == 0) {
                molecule.setProperty("Binder1A2", dontbinds);
            }
            if (prediction == 2.0 && iso == 1) {
                molecule.setProperty("Binder2C9", binds);
            } else if (iso == 1) {
                molecule.setProperty("Binder2C9", dontbinds);
            }
            if (prediction == 2.0 && iso == 2) {
                molecule.setProperty("Binder2C19", binds);
            } else if (iso == 2) {
                molecule.setProperty("Binder2C19", dontbinds);
            }
            if (prediction == 2.0 && iso == 3) {
                molecule.setProperty("Binder2D6", binds);
            } else if (iso == 3) {
                molecule.setProperty("Binder2D6", dontbinds);
            }
            if (prediction == 2.0 && iso == 4) {
                molecule.setProperty("Binder3A4", binds);
            } else if (iso == 4) {
                molecule.setProperty("Binder3A4", dontbinds);
            }

            // Get the most significant signature for classification or the sum of all gradient components for regression.
            List<Double> gradientComponents = new ArrayList<Double>();
            int nOverk = fact(svmModel.nr_class) / (fact(2) * fact(svmModel.nr_class - 2)); // The number of decision functions for a classification.
            double decValues[] = new double[nOverk];
            double lowerPointValue[] = new double[nOverk];
            double higherPointValue[] = new double[nOverk];
            svm.svm_predict_values(svmModel, moleculeArray, decValues);
            lowerPointValue = decValues.clone();
            for (int element = 0; element < moleculeArray.length; element++) {
                // Temporarily increase the descriptor value by one to compute the corresponding component of the gradient of the decision function.
                moleculeArray[element].value = moleculeArray[element].value + 1.00;
                svm.svm_predict_values(svmModel, moleculeArray, decValues);
                higherPointValue = decValues.clone();
                double gradComponentValue = 0.0;

                for (int curDecisionFunc = 0; curDecisionFunc < nOverk; curDecisionFunc++) {
                    if (svmModel.rho[curDecisionFunc] > 0.0) { // Check if the decision function is reversed.
                        gradComponentValue = gradComponentValue + higherPointValue[curDecisionFunc] - lowerPointValue[curDecisionFunc];
                    } else {
                        gradComponentValue = gradComponentValue + lowerPointValue[curDecisionFunc] - higherPointValue[curDecisionFunc];
                    }
                }

                gradientComponents.add(gradComponentValue);
                // Set the value back to what it was.
                moleculeArray[element].value = moleculeArray[element].value - 1.00;

            }

            String significantSignature = "";
            List<Integer> centerAtoms = new ArrayList<Integer>();
            int height = -1;

            if (prediction > 1.5) { // Look for most positive component.
                double maxComponent = -1.0;
                int elementMaxVal = -1;
                for (int element = 0; element < moleculeArray.length; element++) {
                    if (gradientComponents.get(element) > maxComponent) {
                        maxComponent = gradientComponents.get(element);
                        elementMaxVal = element;
                    }
                }
                if (maxComponent > 0.0) {
                    //System.out.println("Max atom: " + moleculeSignaturesAtomNr.get(signatures.get(moleculeArray[elementMaxVal].index-1)) + ", max val: " + gradientComponents.get(elementMaxVal) + ", signature: " + signatures.get(moleculeArray[elementMaxVal].index-1) + ", height: " + moleculeSignaturesHeight.get(signatures.get(moleculeArray[elementMaxVal].index-1)));

                    significantSignature = signatures.get(moleculeArray[elementMaxVal].index - 1);
                    height = moleculeSignaturesHeight.get(signatures.get(moleculeArray[elementMaxVal].index - 1));
                    centerAtoms = moleculeSignaturesAtomNr.get(signatures.get(moleculeArray[elementMaxVal].index - 1));

                } else {
                    //System.out.println("No significant signature.");
                }
            } else {
                double minComponent = 1.0;
                int elementMinVal = -1;
                for (int element = 0; element < moleculeArray.length; element++) {
                    if (gradientComponents.get(element) < minComponent) {
                        minComponent = gradientComponents.get(element);
                        elementMinVal = element;
                    }
                }
                if (minComponent < 0.0) {
                    //System.out.println("Min atom: " + moleculeSignaturesAtomNr.get(signatures.get(moleculeArray[elementMinVal].index-1)) + ", min val: " + gradientComponents.get(elementMinVal) + ", signature: " + signatures.get(moleculeArray[elementMinVal].index-1) + ", height: " + moleculeSignaturesHeight.get(signatures.get(moleculeArray[elementMinVal].index-1)));

                    significantSignature = signatures.get(moleculeArray[elementMinVal].index - 1);
                    height = moleculeSignaturesHeight.get(signatures.get(moleculeArray[elementMinVal].index - 1));
                    centerAtoms = moleculeSignaturesAtomNr.get(signatures.get(moleculeArray[elementMinVal].index - 1));

                } else {
                    //System.out.println("No significant signature.");
                }
            }

            //create list of the atoms that should be marked in html figures
            if (significantSignature.length() > 0) {
                //OK, we got a significant signature, let's define the atoms that should be colored

                for (int centerAtom : centerAtoms) {
                    //match.putAtomResult( centerAtom, match.getClassification() );

                    colorlabels[iso][centerAtom] = 1;

                    int currentHeight = 0;
                    List<Integer> lastNeighbours = new ArrayList<Integer>();
                    lastNeighbours.add(centerAtom);

                    while (currentHeight < height) {

                        List<Integer> newNeighbours = new ArrayList<Integer>();

                        //for all lastNeighbours, get new neighbours
                        for (Integer lastneighbour : lastNeighbours) {
                            for (IAtom nbr : molecule.getConnectedAtomsList(molecule.getAtom(lastneighbour))) {

                                //Set each neighbour atom to overall match classification
                                int nbrAtomNr = molecule.getAtomNumber(nbr);
                                //match.putAtomResult( nbrAtomNr, match.getClassification() );
                                colorlabels[iso][nbrAtomNr] = 1;

                                newNeighbours.add(nbrAtomNr);

                            }
                        }

                        lastNeighbours = newNeighbours;

                        currentHeight++;
                    }
                }
            }

        }
        return colorlabels;
    }

    private static List<String> readSignaturesFile(String signaturesPath) throws Exception {
        List<String> signatures = new ArrayList<String>();
        try {
            InputStream signaturestream = BARDWhichCyp.class.getClassLoader().getResourceAsStream(signaturesPath);
            BufferedReader signaturesReader = new BufferedReader(new InputStreamReader(signaturestream));
            String signature;
            while ((signature = signaturesReader.readLine()) != null) {
                signatures.add(signature);
            }
        } catch (FileNotFoundException e) {
            throw new Exception("Error reading signatures file " + signaturesPath + ": " + e.getMessage());
        } catch (IOException e) {
            throw new Exception("Error reading signatures file " + signaturesPath + ": " + e.getMessage());
        }
        return signatures;

    }


    static int fact(int n) {
        if (n <= 1) {
            return 1;
        } else {
            return n * fact(n - 1);
        }
    }

}
