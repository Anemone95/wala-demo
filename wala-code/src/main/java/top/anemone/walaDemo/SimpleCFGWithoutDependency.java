package top.anemone.walaDemo;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

import top.anemone.walaDemo.callGraph.CallGraphTestUtil;
import top.anemone.walaDemo.utils.StmtFormater;
import top.anemone.walaDemo.wala.AppEntryPoint;


@SuppressWarnings("Duplicates")
public class SimpleCFGWithoutDependency {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCFGWithoutDependency.class);

    public static void main(String[] args) throws CancelException, WalaException, IOException, InvalidClassFileException {
        // the jar can be built from https://github.com/Anemone95/java-sec-code
        String appJar="wala-target/target/wala-target-1.0-SNAPSHOT.jar";
        LOG.info("Create an analysis scope representing the appJar as a J2SE application");
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
                (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
        // using make will not throw exception, but when entry class's superclass not in classpath, it will throw an exception
//        ClassHierarchy cha = ClassHierarchyFactory.make(scope);
        // makeWithPhantom was deprecated
        // ClassHierarchy cha = ClassHierarchyFactory.makeWithPhantom(scope);

        // Recommand makeWithRoot, see https://github.com/wala/WALA/issues/591 for more details
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
                return new AppEntryPoint(mainRef, cha);
            }
        };
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

        LOG.info("Build the call graph");
        com.ibm.wala.ipa.callgraph.CallGraphBuilder cgb = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope, null, null);
        CallGraph cg = cgb.makeCallGraph(options, null);
        PointerAnalysis pa = cgb.getPointerAnalysis();
        LOG.info("Find caller method");

        String caller="top.anemone.walaTarget.NoDependencyDemo#printJson";
        String callee="println";
        int lineNumber=19;
        String[] callerMethod = caller.split("#");
        String clazz = callerMethod[0].replace('.', '/');
        Atom method = Atom.findOrCreateUnicodeAtom(callerMethod[1]);
        CGNode callerNode = null;
        for (CGNode n : cg) {
            if (n.getMethod().getReference().getDeclaringClass().getName().toString().endsWith(clazz) && n.getMethod().getName().equals(method)) {
                callerNode = n;
                break;
            }
        }
        if (callerNode == null) {
            Assertions.UNREACHABLE("failed to find method");
        }

        LOG.info("Find callee statement");
        Statement calleeStmt = null;
        // the closest code will be callee stmt.
        int dist = 987654321;
        IR callerIR = callerNode.getIR();
        for (SSAInstruction s : Iterator2Iterable.make(callerIR.iterateAllInstructions())) {
            if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
                if (call.getCallSite().getDeclaredTarget().getName().toString().equals(callee)) {
                    com.ibm.wala.util.intset.IntSet indices = callerIR.getCallInstructionIndices(call.getCallSite());
                    com.ibm.wala.util.debug.Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                    Statement candidateStmt = new com.ibm.wala.ipa.slicer.NormalStatement(callerNode, indices.intIterator().next());
                    int candidateLineNumber=candidateStmt.getNode().getMethod().getSourcePosition(((NormalStatement)candidateStmt).getInstructionIndex()).getFirstLine();
                    int currentDist=Math.abs(candidateLineNumber-lineNumber);
                    if(currentDist<dist){
                        calleeStmt=candidateStmt;
                        dist=currentDist;
                    }
                }
            }
        }
        if (calleeStmt == null) {
            Assertions.UNREACHABLE("failed to find call to " + callee + " in " + callerNode);
        }

        LOG.info("context-sensitive traditional slice (backward slice)");
        Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.NO_BASE_NO_HEAP;
        Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;
        Collection<Statement> slice = Slicer.computeBackwardSlice(calleeStmt, cg, pa, dOptions, cOptions);

        LOG.info("Pruning SDG");
        SDG<InstanceKey> sdg=new SDG<InstanceKey>(cg, pa, dOptions, cOptions);
        // filter primordial stmt
        Predicate<Statement> filter = o -> slice.contains(o) && !o.toString().contains("Primordial") && o.getKind() == Statement.Kind.NORMAL;
        Graph<Statement> graph = GraphSlicer.prune(sdg, filter);
        for (Statement s : graph) {
//            System.out.println(s);
            // print stmt in a beautiful way
            System.out.println(StmtFormater.format(s));
        }
    }
}
