package top.anemone.walademo;


import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import top.anemone.walademo.callGraph.CallGraphTestUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class SimpleSlice {
    public static void main(String[] args) throws CancelException, WalaException, IOException {
        doSlicing("./example_jar/wala-target-1.0-SNAPSHOT.jar");
    }
    public static void doSlicing(String appJar) throws WalaException, CancelException, IOException {
        // create an analysis scope representing the appJar as a J2SE application
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
                (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
        ClassHierarchy cha = ClassHierarchyFactory.make(scope);

        Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha);
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

        // build the call graph
        com.ibm.wala.ipa.callgraph.CallGraphBuilder cgb = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope, null, null);
        CallGraph cg = cgb.makeCallGraph(options, null);
        PointerAnalysis pa = cgb.getPointerAnalysis();

        // find source method
        String[] sourceMethod="top.anemone.walatarget.Main#main".split("#");
        String clazz=sourceMethod[0].replace('.','/');
        Atom method=Atom.findOrCreateUnicodeAtom(sourceMethod[1]);
        CGNode sourceNode=null;
        for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext(); ) {
            CGNode n = it.next();
            if (n.getMethod().getReference().getDeclaringClass().getName().toString().endsWith(clazz) && n.getMethod().getName().equals(method)) {
                sourceNode=n;
                break;
            }
        }
        if(sourceNode==null){
            Assertions.UNREACHABLE("failed to find method");
        }

        // find seed statement TODO 不是很懂，为什么不能跨函数切片?
        Statement statement = findCallTo(sourceNode, "println");

        Collection<Statement> slice;

        // context-sensitive traditional slice
        slice = Slicer.computeBackwardSlice(statement, cg, pa);
        dumpSlice(slice);
    }


    public static Statement findCallTo(CGNode n, String methodName) {
        IR ir = n.getIR();
        for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
            SSAInstruction s = it.next();
            if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
                if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)) {
                    com.ibm.wala.util.intset.IntSet indices = ir.getCallInstructionIndices(call.getCallSite());
                    com.ibm.wala.util.debug.Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                    return new com.ibm.wala.ipa.slicer.NormalStatement(n, indices.intIterator().next());
                }
            }
        }
        Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
        return null;
    }

    public static void dumpSlice(Collection<Statement> slice) {
        for (Statement s : slice) {
            System.err.println(s);
        }
    }
}
