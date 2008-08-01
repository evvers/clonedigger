package org.clonedigger.actions;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.internal.browser.WebBrowserEditorInput;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.osgi.framework.Bundle;
import org.python.pydev.navigator.elements.IWrappedResource;

/**
 * Our sample action implements workbench action delegate.
 * The action proxy will be created by the workbench and
 * shown in the UI. When the user tries to use the action,
 * this delegate will be created and execution will be 
 * delegated to it.
 * @see IWorkbenchWindowActionDelegate
 */
@SuppressWarnings("restriction")
public class DigAction implements IViewActionDelegate, IWorkbenchWindowActionDelegate, IObjectActionDelegate, IPageChangedListener {
	boolean WINDOWS = java.io.File.separatorChar == '\\';
	Set<String> selectedFiles = new HashSet<String>();
	Set<IResource> selectedResources = new HashSet<IResource>();
	Set<IResource> grayedResources = new HashSet<IResource>();
	Process digProcess = null;
	Thread digThread = null;
	private String htmFile;
	private ProcessBuilder pb;
	private DigWizard digWizard;
	
	/**
	 * The constructor
	 */
	public DigAction() {
	}
	
	public void dispose() {
	}
	
	public void init(IViewPart view) {
		//this.view = view;
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		//part = targetPart;		
	}
	
	public void init(IWorkbenchWindow window) {
	}
	
	class ResourcesPage extends WizardPage implements ITreeContentProvider, ILabelProvider, ICheckStateListener
	{
		private Combo langCombo;
		private CheckboxTreeViewer resourcesTree;
		private ILabelProvider labelProvider;
		private Button fastMode;
		private Spinner cloneSize;
		private Spinner cloneDist;

		public ResourcesPage() {
			super("ResourcesPage");
			setTitle("Please select files to dig");
			labelProvider = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
		}

		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout gl = new GridLayout();
			GridData gd;
			gl.numColumns = 2;
			gl.horizontalSpacing = 12;
			composite.setLayout(gl);
			new Label(composite, SWT.NONE).setText("Select language:");					
			
			Group opsGroup = new Group(composite, SWT.BEGINNING);
			opsGroup.setText("Dig options");
			opsGroup.setLayout(new GridLayout(5, false));
			
			gd = new GridData(GridData.BEGINNING);
			gd.verticalSpan = 2;
			opsGroup.setLayoutData(gd);
			
			fastMode = new Button(opsGroup, SWT.CHECK);
			fastMode.setText("Fast mode");
			fastMode.setToolTipText(
					"Find only clones, which differ in variable and function names and constants." +
					"Use this option if you don't want to wait.");
			Label label = new Label(opsGroup, SWT.NONE);
			label.setText("  Clone size:");
			label.setToolTipText("The minimum clone size (in lines of code)");
			cloneSize = new Spinner(opsGroup, SWT.BORDER);
			cloneSize.setMinimum(1);
			cloneSize.setMaximum(99);
			cloneSize.setTextLimit(2);
			cloneSize.setToolTipText(label.getToolTipText());
			label = new Label(opsGroup, SWT.NONE);
			label.setText("  Clone distance:");
			label.setToolTipText("The maximum amount of differences between fragments in clone pair");
			cloneDist = new Spinner(opsGroup, SWT.BORDER);
			cloneDist.setMinimum(1);
			cloneDist.setMaximum(99);
			cloneDist.setTextLimit(2);
			cloneDist.setToolTipText(label.getToolTipText());
			
			langCombo = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
			langCombo.add("Python");
			langCombo.add("Java");
			langCombo.select(0);
			gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
			langCombo.setLayoutData(gd);
			langCombo.addSelectionListener(new SelectionListener() 
			{

				public void widgetDefaultSelected(SelectionEvent e) {
					if(resourcesTree != null)
						resourcesTree.refresh();
				}
 
				public void widgetSelected(SelectionEvent e) {
					if(resourcesTree != null) {
						resourcesTree.refresh();
						resourcesTree.setCheckedElements(selectedResources.toArray());
						resourcesTree.setGrayedElements(grayedResources.toArray());
					}
					if(langCombo.getSelectionIndex() == 0) {
						cloneSize.setSelection(5);
						cloneDist.setSelection(5);
					} else {
						cloneSize.setSelection(10);
						cloneDist.setSelection(7);
					}
						
				}
				
			});
			langCombo.notifyListeners(SWT.Selection, new Event());
									
			label = new Label(composite, SWT.NONE);
			label.setText("Select files:");
			gd = new GridData(SWT.BEGINNING);
			gd.horizontalSpan = 2;
			label.setLayoutData(gd);
			resourcesTree = new CheckboxTreeViewer(composite);
			//resourcesTree.setLabelProvider(this);
			//resourcesTree.setContentProvider(this);
			resourcesTree.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
			resourcesTree.setContentProvider(new WorkbenchContentProvider()
			{
				public Object[] getChildren(Object o) {
	                if (o instanceof IContainer) {
	                    IResource[] members = null;
	                    try {
	                        members = ((IContainer) o).members();
	                    } catch (CoreException e) {
	                        return new Object[0];
	                    }

	                    List<Object> results = new ArrayList<Object>();
	                    for(Object member: members) {
	                        if(member instanceof IFile) {
 	                         	if(langCombo.getSelectionIndex() == 0 &&
	                         			((IFile)member).getFileExtension().equals("py") ||
	                         	   langCombo.getSelectionIndex() == 1 &&
	                        			((IFile)member).getFileExtension().equals("java"))
 	                        		results.add(member);
	                        } else results.add(member);
	                    }
	                    return results.toArray();
	                } 
	                return new Object[0];
	            }
			});
			resourcesTree.addCheckStateListener(this);
			resourcesTree.setInput(ResourcesPlugin.getWorkspace().getRoot());
			gd = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
			gd.horizontalSpan = 2;
			resourcesTree.getControl().setLayoutData(gd);
			resourcesTree.refresh();
			resourcesTree.setCheckedElements(selectedResources.toArray());
			resourcesTree.setGrayedElements(grayedResources.toArray());
			
			setControl(composite); 
		}

		public Object[] getChildren(Object parentElement) {
			if(parentElement instanceof IContainer)
				try {
					return ((IContainer)parentElement).members();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return new Object[0];
		}

		public Object getParent(Object element) {
			if(element instanceof IResource)
				((IResource)element).getParent();
			return null;
		}

		public boolean hasChildren(Object element) {
			if(element instanceof IContainer)
				try {
					return ((IContainer)element).members().length > 0;
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return false;
		}

		public Object[] getElements(Object inputElement) {
			if(inputElement instanceof IContainer)
				try {
					return ((IContainer)inputElement).members();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return new Object[0];
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		public Image getImage(Object element) {
			return labelProvider.getImage(element);
		}

		public String getText(Object element) {
			return labelProvider.getText(element);
		}

		public void addListener(ILabelProviderListener listener) {
			labelProvider.addListener(listener);
		}

		public boolean isLabelProperty(Object element, String property) {
			return labelProvider.isLabelProperty(element, property);
		}

		public void removeListener(ILabelProviderListener listener) {
			labelProvider.removeListener(listener);
		}

		public void checkStateChanged(CheckStateChangedEvent event) {
			boolean checked = event.getChecked();
			IResource res = (IResource) event.getElement();
			selectResource(res, checked); 
			resourcesTree.setCheckedElements(selectedResources.toArray());
			resourcesTree.setGrayedElements(grayedResources.toArray());
		}
		
	}
	
	class ConsolePage extends WizardPage
	{
		Text console;

		public ConsolePage() {
			super("ConsolePage");
			setTitle("Running clonedigger");
			setPageComplete(false);
		}

		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout gl = new GridLayout();
			int ncol = 1;
			gl.numColumns = ncol;
			composite.setLayout(gl);
			
			//new Label(composite, SWT.NONE).setText("Output console:");
			
			console = new Text(composite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
			console.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
			console.setBackground(new Color(null, 0,0,0));
			console.setForeground(new Color(null, 255,255,255));
			/*try {
				console.setFont(new org.eclipse.swt.graphics.Font(null, "Terminal", 14, 0));
			} 
			catch(Exception e)
			{
				e.printStackTrace();
			}/**/
						
			setControl(composite);
		}		

	}
	
	class DigWizard extends Wizard
	{
		private ResourcesPage resourcePage;
		private ConsolePage consolePage;

		public void addPages() {
			super.addPages();
			addPage(resourcePage = new ResourcesPage());
			addPage(consolePage = new ConsolePage());
			setWindowTitle("Dig clones");
		}

		public boolean performFinish() {
			if((new java.io.File(htmFile)).exists())
				try {

					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

					IEditorInput htmInput = null;
					htmInput = new WebBrowserEditorInput(new URL("file:/" + htmFile.replaceAll("^/+", "")), 0);

					//IEditorPart	htmEditor = (IEditorPart)
					page.openEditor(htmInput,
						"org.clonedigger.resultbrowser");
						//"org.eclipse.ui.browser.editor");		

				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			return true;
		}
		
		public boolean performCancel() {
			if(digProcess == null) return true;
			digProcess.destroy();
			try {
				digProcess.waitFor();
				digThread.interrupt();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			digProcess = null;
			digThread = null;
			return true;
		}
	}
	
	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) 
	{
		if(!PlatformUI.getWorkbench().saveAllEditors(true)) return;
		
		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		digWizard = new DigWizard();
		WizardDialog wd = new WizardDialog(shell, digWizard);
		wd.addPageChangedListener(this);
		wd.open();
	}

	public void selectResource(IResource res, boolean select)
	{
		if(res == null) return;
		if(select && selectedResources.contains(res) && !grayedResources.contains(res)) return;
		if(res instanceof IContainer)
			try {
				for(IResource subRes: ((IContainer)res).members())
					selectResource(subRes, select);				
			} catch (CoreException e) {
				e.printStackTrace();
			}
		else if(res instanceof IFile)
		{
			if(select)
				selectedFiles.add(res.getLocation().toOSString());
			else
				selectedFiles.remove(res.getLocation().toOSString());
		}
		
		if(select) 
			selectedResources.add(res);
		else
			selectedResources.remove(res);
		grayedResources.remove(res);
		
		res = res.getParent();
		while(res != null)
		{
			selectedResources.add(res);
			grayedResources.add(res);
			res = res.getParent();
		}
	}
	
	/**
	 * Selection in the workbench has been changed. We 
	 * can change the state of the 'real' action here
	 * if we want, but this can only happen after 
	 * the delegate has been created.
	 * @see IWorkbenchWindowActionDelegate#selectionChanged
	 */
	public void selectionChanged(IAction action, ISelection selection) 
	{
		IStructuredSelection sel = (IStructuredSelection)selection;
		selectedFiles.clear();
		selectedResources.clear();
		grayedResources.clear();
		action.setEnabled(true);
		for(Object obj: sel.toArray())
		{
			IResource res = null;
			if(obj instanceof IResource) 
				res = (IResource)obj;
			if(obj instanceof IWrappedResource)
			{
  				Object unwrap = ((IWrappedResource)obj).getActualObject();
				if(unwrap instanceof IResource)
				res = (IResource) unwrap;
			}
			if(obj instanceof IJavaElement) 
				res = ((IJavaElement)obj).getResource();
			//if(res == null) action.setEnabled(false);
			selectResource(res, true);
		}
	}

	public void pageChanged(PageChangedEvent event) {
		IDialogPage page = (IDialogPage) event.getSelectedPage();
		if(!(page instanceof ConsolePage))
		{
			if(digProcess != null)
			{
				digProcess.destroy();
				try {
					digProcess.waitFor();
					digThread.interrupt();
					//digThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				digProcess = null;
				digThread = null;
			}
			return;
		}
		int langidx = digWizard.resourcePage.langCombo.getSelectionIndex();
		final ConsolePage consolePage = (ConsolePage) page;
		consolePage.console.setText("");
		consolePage.setPageComplete(false);
		
		String ops = " --links-for-eclipse";
		if(langidx == 1) ops += " --lang=java";
		if(digWizard.resourcePage.fastMode.getSelection()) ops += " --fast";
		ops += " --size-threshold=" + digWizard.resourcePage.cloneSize.getSelection();
		ops += " --distance-threshold=" + digWizard.resourcePage.cloneDist.getSelection(); 
		
		String path = "";
		for(String f: selectedFiles)
		{
			f = f.replaceAll("\\\\", "/"); //fix bug in browsersupport, which broke links with "\"
			if(langidx == 0 && f.endsWith(".py")) path += "\"" + f + "\" ";
			if(langidx == 1 && f.endsWith(".java")) path += "\"" + f + "\" ";
		}
		
		try {
			File tmpfile = File.createTempFile("cde_output", ".htm");
			htmFile = tmpfile.getAbsolutePath();
			tmpfile.deleteOnExit();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Bundle bundle = Platform.getBundle("org.clonedigger");
		String runpath = "";
		try {
			runpath = FileLocator.toFileURL(bundle.getEntry("runclonedigger.py")).getPath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(WINDOWS) runpath = runpath.replaceAll("^/+", "");
		
		pb = new ProcessBuilder();
		if(WINDOWS)
		{
			//cmd /C ""..." "..."  > 2>&1"
			pb.command().add("cmd");
			pb.command().add("/C");
			pb.command().add(
					"\"\"" + runpath + "\" " +
					//"\"\"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "\\runclonedigger.py\" " +
					ops +
					" --output=\"" + htmFile + "\" " +
					path +
					" 2>&1 \"");
		}
		else
		{
			//sh -c python "..." "..." > 2>&1
			pb.command().add("sh");
			pb.command().add("-c");
			pb.command().add(
					"python \"" + runpath + "\" " +
					//"python \"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "/runclonedigger.py\" " +
					ops +
					" --output=\"" + htmFile + "\" " +
					path +
					" 2>&1 ");
		}
		pb.redirectErrorStream(true);
		String ppath = (new File(runpath)).getParent() + "/CloneDigger";
		//(new File(ppath)).mkdir();
		pb.environment().put("PYTHONPATH", ppath);
		
		System.out.println("pythonexec: " + pb.command().toString()); 
		
		consolePage.console.append("Running clonedigger...\n\n");

		digThread = new Thread(new Runnable() {
			public void run() {
				final char[] buf = new char[1024]; 
				do
				{
					digProcess = null;
					try {
						digProcess = pb.start();
						InputStreamReader pi = new InputStreamReader(digProcess.getInputStream());
						
						while(true)
						{
							final int len = pi.read(buf);
							if(len < 0) break;
							Display.getDefault().syncExec(new Runnable() {
								public void run() {
									consolePage.console.append(new String(buf, 0, len));		
								}});
						}
					} catch (IOException e) {
						e.printStackTrace();
					} 

					Display.getDefault().syncExec(new Runnable() {
						public void run() {
							consolePage.console.append("\n");
						}});
					
					try {
						//On *nix systems output console closing a moment before terminating process
						digProcess.waitFor();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				} while(digProcess.exitValue() == 143);
				Display.getDefault().syncExec(new Runnable() {
					public void run() {
						digProcess = null;
						digThread = null; 
						if((new java.io.File(htmFile)).exists())
						{
							consolePage.console.append("Press finish to view results...");
							consolePage.setPageComplete(true);
						}
						else
							consolePage.console.append("No output found...");
					}});				
			}});
		digThread.start();
	}
}