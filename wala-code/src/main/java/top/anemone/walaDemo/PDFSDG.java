/*
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package top.anemone.walaDemo;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.MethodEntryStatement;
import com.ibm.wala.ipa.slicer.MethodExitStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.anemone.walaDemo.callGraph.CallGraphTestUtil;
import top.anemone.walaDemo.properties.WalaExamplesProperties;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * This simple example WALA application builds an SDG and fires off ghostview to viz a DOT
 * representation.
 *
 * @author sfink
 */
@SuppressWarnings("Duplicates")
public class PDFSDG {

  private static final String PDF_FILE = "sdg.pdf";
  private static final Logger LOG = LoggerFactory.getLogger(PDFSDG.class);

  /**
   * Usage: GVSDG -appJar [jar file name] -mainclass [main class]
   *
   * <p>The "jar file name" should be something like "c:/temp/testdata/java_cup.jar"
   */
  public static void main(String[] args)
      throws IllegalArgumentException, CancelException, IOException {
    run(args);
  }

  public static void run(String[] args)
      throws IllegalArgumentException, CancelException, IOException {
    Properties p = CommandLine.parse(args);
    validateCommandLine(p);
    run(
        p.getProperty("appJar"),
        p.getProperty("mainClass"),
        getDataDependenceOptions(p),
        getControlDependenceOptions(p));
  }

  public static DataDependenceOptions getDataDependenceOptions(Properties p) {
    String d = p.getProperty("dd", "full");
    for (DataDependenceOptions result : DataDependenceOptions.values()) {
      if (d.equals(result.getName())) {
        return result;
      }
    }
    Assertions.UNREACHABLE("unknown data data dependence option: " + d);
    return null;
  }

  public static ControlDependenceOptions getControlDependenceOptions(Properties p) {
    String d = p.getProperty("cd", "full");
    for (ControlDependenceOptions result : ControlDependenceOptions.values()) {
      if (d.equals(result.getName())) {
        return result;
      }
    }
    Assertions.UNREACHABLE("unknown control data dependence option: " + d);
    return null;
  }

  /** @param appJar something like "c:/temp/testdata/java_cup.jar" */
  public static void run(
      String appJar,
      String mainClass,
      DataDependenceOptions dOptions,
      ControlDependenceOptions cOptions)
      throws IllegalArgumentException, CancelException, IOException {
    try {
      LOG.info("Adding scope...");
      AnalysisScope scope =
          AnalysisScopeReader.makeJavaBinaryAnalysisScope(
              appJar, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

      // generate a WALA-consumable wrapper around the incoming scope object

      ClassHierarchy cha = ClassHierarchyFactory.make(scope);
      Iterable<Entrypoint> entrypoints =
          Util.makeMainEntrypoints(scope, cha, mainClass);
      AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

      LOG.info("Building Call Graph...");
      CallGraphBuilder<InstanceKey> builder =
          Util.makeZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
      CallGraph cg = builder.makeCallGraph(options, null);

      LOG.info("Building SDG...");
      final PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
      SDG<?> sdg = new SDG<>(cg, pointerAnalysis, dOptions, cOptions);
//      try {
//        GraphIntegrity.check(sdg);
//      } catch (UnsoundGraphException e1) {
//        e1.printStackTrace();
//        Assertions.UNREACHABLE();
//      }
//      System.out.println(sdg);

      LOG.info("Prune SDG...");
      Graph<Statement> g = pruneSDG(sdg);

      LOG.info("Writing pdf...");
      Properties p = null;
      try {
        p = WalaExamplesProperties.loadProperties();
        p.putAll(WalaProperties.loadProperties());
      } catch (WalaException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
      }
      String psFile = p.getProperty(WalaProperties.OUTPUT_DIR) + File.separatorChar + PDF_FILE;

      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      DotUtil.dotify(g, makeNodeDecorator(), PDFTypeHierarchy.DOT_FILE, psFile, dotExe);

      System.out.println("File saved in "+psFile);

    } catch (WalaException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static Graph<Statement> pruneSDG(final SDG<?> sdg) {
    Predicate<Statement> f =
        s -> {
          if (s.getNode().equals(sdg.getCallGraph().getFakeRootNode())) {
            return false;
          } else if (s instanceof MethodExitStatement || s instanceof MethodEntryStatement) {
            return false;
          } else {
            return true;
          }
        };
    return GraphSlicer.prune(sdg, f);
  }

  private static NodeDecorator<Statement> makeNodeDecorator() {
    return s -> {
      switch (s.getKind()) {
        case HEAP_PARAM_CALLEE:
        case HEAP_PARAM_CALLER:
        case HEAP_RET_CALLEE:
        case HEAP_RET_CALLER:
          HeapStatement h = (HeapStatement) s;
          return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
        case EXC_RET_CALLEE:
        case EXC_RET_CALLER:
        case NORMAL:
        case NORMAL_RET_CALLEE:
        case NORMAL_RET_CALLER:
        case PARAM_CALLEE:
        case PARAM_CALLER:
        case PHI:
        default:
          return s.toString();
      }
    };
  }

  /**
   * Validate that the command-line arguments obey the expected usage.
   *
   * <p>Usage:
   *
   * <ul>
   *   <li>args[0] : "-appJar"
   *   <li>args[1] : something like "c:/temp/testdata/java_cup.jar"
   *   <li>args[2] : "-mainClass"
   *   <li>args[3] : something like "Lslice/TestRecursion"
   * </ul>
   *
   * @throws UnsupportedOperationException if command-line is malformed.
   */
  static void validateCommandLine(Properties p) {
    if (p.get("appJar") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
    if (p.get("mainClass") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
  }
}
