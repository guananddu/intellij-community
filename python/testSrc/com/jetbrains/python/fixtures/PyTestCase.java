/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.fixtures;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/")
public abstract class PyTestCase extends UsefulTestCase {
  public static final String PYTHON_2_MOCK_SDK = "2.7";
  public static final String PYTHON_3_MOCK_SDK = "3.4";

  private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor(PYTHON_2_MOCK_SDK);
  protected static final PyLightProjectDescriptor ourPy3Descriptor = new PyLightProjectDescriptor(PYTHON_3_MOCK_SDK);
  private static final String PARSED_ERROR_MSG = "Operations should have been performed on stubs but caused file to be parsed";

  protected CodeInsightTestFixture myFixture;

  @Nullable
  protected static VirtualFile getVirtualFileByName(String fileName) {
    final VirtualFile path = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
    if (path != null) {
      refreshRecursively(path);
      return path;
    }
    return null;
  }

  /**
   * Reformats currently configured file.
   */
  protected final void reformatFile() {
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        doPerformFormatting();
      }
    });
  }

  private void doPerformFormatting() throws IncorrectOperationException {
    final PsiFile file = myFixture.getFile();
    final TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    createTempDirFixture());
    myFixture.setUp();

    myFixture.setTestDataPath(getTestDataPath());
  }

  /**
   * @return fixture to be used as temporary dir.
   */
  @NotNull
  protected TempDirTestFixture createTempDirFixture() {
    return new LightTempDirTestFixtureImpl(true); // "tmp://" dir by default
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  @Override
  protected void tearDown() throws Exception {
    setLanguageLevel(null);
    myFixture.tearDown();
    myFixture = null;
    final PythonLanguageLevelPusher levelPusher = Extensions.findExtension(FilePropertyPusher.EP_NAME, PythonLanguageLevelPusher.class);
    levelPusher.flushLanguageLevelCache();
    super.tearDown();
    clearFields(this);
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyDescriptor;
  }

  protected PsiReference findReferenceBySignature(final String signature) {
    int pos = findPosBySignature(signature);
    return findReferenceAt(pos);
  }

  protected PsiReference findReferenceAt(int pos) {
    return myFixture.getFile().findReferenceAt(pos);
  }

  protected int findPosBySignature(String signature) {
    return PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(myFixture.getFile()).getText().indexOf(signature);
  }

  protected void setLanguageLevel(@Nullable LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
  }

  protected void runWithLanguageLevel(@NotNull LanguageLevel languageLevel, @NotNull Runnable action) {
    setLanguageLevel(languageLevel);
    try {
      action.run();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  protected void runWithDocStringFormat(@NotNull DocStringFormat format, @NotNull Runnable runnable) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    final DocStringFormat oldFormat = settings.getFormat();
    settings.setFormat(format);
    try {
      runnable.run();
    }
    finally {
      settings.setFormat(oldFormat);
    }
  }

  /**
   * Searches for quickfix itetion by its class
   *
   * @param clazz quick fix class
   * @param <T>   quick fix class
   * @return quick fix or null if nothing found
   */
  @Nullable
  public <T extends LocalQuickFix> T findQuickFixByClassInIntentions(@NotNull final Class<T> clazz) {

    for (final IntentionAction action : myFixture.getAvailableIntentions()) {
      if ((action instanceof QuickFixWrapper)) {
        final QuickFixWrapper quickFixWrapper = (QuickFixWrapper)action;
        final LocalQuickFix fix = quickFixWrapper.getFix();
        if (clazz.isInstance(fix)) {
          @SuppressWarnings("unchecked")
          final T result = (T)fix;
          return result;
        }
      }
    }
    return null;
  }


  protected static void assertNotParsed(PyFile file) {
    assertNull(PARSED_ERROR_MSG, ((PyFileImpl)file).getTreeElement());
  }

  /**
   * @param name
   * @return class by its name from file
   */
  @NotNull
  protected PyClass getClassByName(@NotNull final String name) {
    return myFixture.findElementByText("class " + name, PyClass.class);
  }

  /**
   * @see #moveByText(com.intellij.testFramework.fixtures.CodeInsightTestFixture, String)
   */
  protected void moveByText(@NotNull final String testToFind) {
    moveByText(myFixture, testToFind);
  }

  /**
   * Finds some text and moves cursor to it (if found)
   *
   * @param fixture    test fixture
   * @param testToFind text to find
   * @throws AssertionError if element not found
   */
  public static void moveByText(@NotNull final CodeInsightTestFixture fixture, @NotNull final String testToFind) {
    final PsiElement element = fixture.findElementByText(testToFind, PsiElement.class);
    assert element != null : "No element found by text: " + testToFind;
    fixture.getEditor().getCaretModel().moveToOffset(element.getTextOffset());
  }

  /**
   * Finds all usages of element. Works much like method in {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#findUsages(com.intellij.psi.PsiElement)},
   * but supports {@link com.intellij.find.findUsages.CustomUsageSearcher} and {@link com.intellij.psi.search.searches.ReferencesSearch} as well
   *
   * @param element what to find
   * @return usages
   */
  @NotNull
  protected Collection<PsiElement> findUsage(@NotNull final PsiElement element) {
    final Collection<PsiElement> result = new ArrayList<PsiElement>();
    final CollectProcessor<Usage> usageCollector = new CollectProcessor<Usage>();
    for (final CustomUsageSearcher searcher : CustomUsageSearcher.EP_NAME.getExtensions()) {
      searcher.processElementUsages(element, usageCollector, new FindUsagesOptions(myFixture.getProject()));
    }
    for (final Usage usage : usageCollector.getResults()) {
      if (usage instanceof PsiElementUsage) {
        result.add(((PsiElementUsage)usage).getElement());
      }
    }
    for (final PsiReference reference : ReferencesSearch.search(element).findAll()) {
      result.add(reference.getElement());
    }

    for (final UsageInfo info : myFixture.findUsages(element)) {
      result.add(info.getElement());
    }

    return result;
  }

  /**
   * Returns elements certain element allows to navigate to (emulates CTRL+Click, actually).
   * You need to pass element as argument or
   * make sure your fixture is configured for some element (see {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#getElementAtCaret()})
   *
   * @param element element to fetch navigate elements from (may be null: element under caret would be used in this case)
   * @return elements to navigate to
   */
  @NotNull
  protected Set<PsiElement> getElementsToNavigate(@Nullable final PsiElement element) {
    final Set<PsiElement> result = new HashSet<PsiElement>();
    final PsiElement elementToProcess = ((element != null) ? element : myFixture.getElementAtCaret());
    for (final PsiReference reference : elementToProcess.getReferences()) {
      final PsiElement directResolve = reference.resolve();
      if (directResolve != null) {
        result.add(directResolve);
      }
      if (reference instanceof PsiPolyVariantReference) {
        for (final ResolveResult resolveResult : ((PsiPolyVariantReference)reference).multiResolve(true)) {
          result.add(resolveResult.getElement());
        }
      }
    }
    return result;
  }

  /**
   * Clears provided file
   *
   * @param file file to clear
   */
  protected void clearFile(@NotNull final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (final PsiElement element : file.getChildren()) {
              element.delete();
            }
          }
        });
      }
    }, null, null);
  }

  /**
   * Runs refactoring using special handler
   *
   * @param handler handler to be used
   */
  protected void refactorUsingHandler(@NotNull final RefactoringActionHandler handler) {
    final Editor editor = myFixture.getEditor();
    assertInstanceOf(editor, EditorEx.class);
    handler.invoke(myFixture.getProject(), editor, myFixture.getFile(), ((EditorEx)editor).getDataContext());
  }

  /**
   * Configures project by some path. It is here to emulate {@link com.intellij.platform.PlatformProjectOpenProcessor}
   *
   * @param path         path to open
   * @param configurator configurator to use
   */
  protected void configureProjectByProjectConfigurators(@NotNull final String path,
                                                        @NotNull final DirectoryProjectConfigurator configurator) {
    final VirtualFile newPath =
      myFixture.copyDirectoryToProject(path, String.format("%s%s%s", "temp_for_project_conf", File.pathSeparator, path));
    final Ref<Module> moduleRef = new Ref<Module>(myFixture.getModule());
    configurator.configureProject(myFixture.getProject(), newPath, moduleRef);
  }

  public static String getHelpersPath() {
    return new File(PythonHelpersLocator.getPythonCommunityPath(), "helpers").getPath();
  }

  /**
   * Creates run configuration from right click menu
   *
   * @param fixture       test fixture
   * @param expectedClass expected class of run configuration
   * @param <C>           expected class of run configuration
   * @return configuration (if created) or null (otherwise)
   */
  @Nullable
  public static <C extends RunConfiguration> C createRunConfigurationFromContext(
    @NotNull final CodeInsightTestFixture fixture,
    @NotNull final Class<C> expectedClass) {
    final DataContext context = DataManager.getInstance().getDataContext(fixture.getEditor().getComponent());
    for (final RunConfigurationProducer<?> producer : RunConfigurationProducer.EP_NAME.getExtensions()) {
      final ConfigurationFromContext fromContext = producer.createConfigurationFromContext(ConfigurationContext.getFromContext(context));
      if (fromContext == null) {
        continue;
      }
      final C result = PyUtil.as(fromContext.getConfiguration(), expectedClass);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Compares sets with string sorting them and displaying one-per-line to make comparision easier
   *
   * @param message  message to display in case of error
   * @param actual   actual set
   * @param expected expected set
   */
  protected static void compareStringSets(@NotNull final String message,
                                          @NotNull final Set<String> actual,
                                          @NotNull final Set<String> expected) {
    final Joiner joiner = Joiner.on("\n");
    Assert.assertEquals(message, joiner.join(new TreeSet<String>(actual)), joiner.join(new TreeSet<String>(expected)));
  }


  /**
   * Clicks certain button in document on caret position
   *
   * @param action what button to click (const from {@link IdeActions}) (btw, there should be some way to express it using annotations)
   * @see IdeActions
   */
  protected final void pressButton(@NotNull final String action) {
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        myFixture.performEditorAction(action);
      }
    }, "", null);
  }

  @NotNull
  protected CommonCodeStyleSettings getCommonCodeStyleSettings() {
    return getCodeStyleSettings().getCommonSettings(PythonLanguage.getInstance());
  }

  @NotNull
  protected PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  @NotNull
  protected CodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(myFixture.getProject());
  }

  @NotNull
  protected CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    //noinspection ConstantConditions
    return getCommonCodeStyleSettings().getIndentOptions();
  }
}

