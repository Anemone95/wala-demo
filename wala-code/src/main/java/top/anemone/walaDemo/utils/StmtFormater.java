package top.anemone.walaDemo.utils;


import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstruction;


public class StmtFormater {
    public static String format(Statement stmt){
        switch (stmt.getKind()) {
            case HEAP_PARAM_CALLEE:
            case HEAP_PARAM_CALLER:
            case HEAP_RET_CALLEE:
            case HEAP_RET_CALLER:
                HeapStatement h = (HeapStatement) stmt;
                return stmt.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
            case NORMAL:
                NormalStatement n = (NormalStatement) stmt;
                SSAInstruction instruction = n.getInstruction();

                String lineStr = "";
                try {
                    IMethod method = n.getNode().getMethod();
                    lineStr += "in method:" + method.getName() + ", at line:"
                            + method.getSourcePosition(n.getInstructionIndex()).getFirstLine();
                } catch (InvalidClassFileException e) {
                    // TODO
                }

                lineStr += ", inst:" + instruction;
                return lineStr;
            case PARAM_CALLEE:
                ParamCallee paramCallee = (ParamCallee) stmt;
                return stmt.getKind() + " " + paramCallee.getValueNumber() + "\\n" + stmt.getNode().getMethod().getName();
            case PARAM_CALLER:
                ParamCaller paramCaller = (ParamCaller) stmt;
                return stmt.getKind() + " " + paramCaller.getValueNumber() + "\\n" + stmt.getNode().getMethod().getName()
                        + "\\n" + paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName();
            case EXC_RET_CALLEE:
            case EXC_RET_CALLER:
            case NORMAL_RET_CALLEE:
            case NORMAL_RET_CALLER:
            case PHI:
            default:
                return stmt.toString();
        }
    }
}
