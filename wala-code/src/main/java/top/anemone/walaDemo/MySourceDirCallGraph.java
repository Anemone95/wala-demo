package top.anemone.walaDemo;

import com.ibm.wala.cast.java.ecj.util.SourceDirCallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;

import java.io.IOException;

public class MySourceDirCallGraph {
    public static void main(String[] args) throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        SourceDirCallGraph.main(args);
    }
}
