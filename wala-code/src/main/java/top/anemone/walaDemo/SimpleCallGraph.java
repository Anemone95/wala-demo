package top.anemone.walaDemo;


import com.ibm.wala.cast.ipa.callgraph.CAstCallGraphUtil;
import com.ibm.wala.classLoader.JarStreamModule;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.*;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.*;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.viz.DotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.anemone.walaDemo.callGraph.CallGraphTestUtil;
import top.anemone.walaDemo.utils.StmtFormater;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

@SuppressWarnings("Duplicates")
public class SimpleCallGraph {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCallGraph.class);

    public static void main(String[] args) throws CancelException, WalaException, IOException, InvalidClassFileException {

        Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.NO_BASE_NO_HEAP;
        Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;
        String appJar = "wala-target/target/wala-target-1.0-SNAPSHOT.jar";

        LOG.info("Create an analysis scope representing the appJar as a J2SE application");
//        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
//                (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
        AnalysisScope scope = AnalysisScopeReader.readJavaScope("smallScope.txt",
                (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS),new AppClassloader(new File[]{}));
        scope.addToScope(ClassLoaderReference.Application, new JarStreamModule(new FileInputStream(appJar)));
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

        LOG.info("Set entrypoints");
        String[] srcCls = {"Ltop/anemone/walaTarget/NoDependencyDemo"};
        String[] srcFuncs= {"main"};
        String[] srcRefs = {"([Ljava/lang/String;)V"};
        Iterable<Entrypoint> entrypoints = ()-> new Iterator<Entrypoint>() {
            private int index = 0;

            @Override
            public void remove() {
                Assertions.UNREACHABLE();
            }

            @Override
            public boolean hasNext() {
                return index < srcCls.length;
            }

            @Override
            public Entrypoint next() {
                TypeReference T =
                        TypeReference.findOrCreate(scope.getApplicationLoader(),
                                TypeName.string2TypeName(srcCls[index]));
                MethodReference mainRef =
                        MethodReference.findOrCreate(T,
                                Atom.findOrCreateAsciiAtom(srcFuncs[index]),
                                Descriptor.findOrCreateUTF8(srcRefs[index]));
                index++;
                return new DefaultEntrypoint(mainRef, cha);
            }
        };
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

        LOG.info("Build the call graph");
        CallGraphBuilder<?> cgb = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope, null, null);
        CallGraph cg = cgb.makeCallGraph(options, null);
        PointerAnalysis<?> pa = cgb.getPointerAnalysis();
        SSAContextInterpreter interp = (SSAContextInterpreter) ((PropagationCallGraphBuilder)cgb).getContextInterpreter();
        for (CGNode n : cg) {
            if (!n.toString().contains("Ltop/anemone/walaTarget"))
                continue;
            System.err.print("callees of node " + CAstCallGraphUtil.getShortName(n) + " : [");
            boolean fst = true;
            for (CGNode child : Iterator2Iterable.make(cg.getSuccNodes(n))) {
                if (fst) fst = false;
                else System.err.print(", ");
                System.err.print(CAstCallGraphUtil.getShortName(child));
            }
            System.err.println("]");
            System.err.println("\nIR of node " + n.getGraphNodeId() + ", context " + n.getContext());
            IRView ir = interp.getIRView(n);
            if (ir != null) {
                System.err.println(ir);
            } else {
                System.err.println("no IR!");
            }
        }

        System.err.println("pointer analysis");
        for (PointerKey n : pa.getPointerKeys()) {
            if (!n.toString().contains("Ltop/anemone/walaTarget"))
                continue;
            try {
                System.err.println((n + " --> " + pa.getPointsToSet(n)));
            } catch (Throwable e) {
                System.err.println(("error computing set for " + n));
            }
        }
    }

}
