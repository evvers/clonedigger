package org.clonedigger.actions;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogPage;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.*;
import org.eclipse.ui.internal.browser.WebBrowserEditorInput;
import org.eclipse.ui.internal.util.Util;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.osgi.framework.Bundle;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
public class LabelAction implements IViewActionDelegate, IEditorActionDelegate, IObjectActionDelegate, IPageChangedListener {
	//private IEditorPart editor;
	//private IViewPart view;
	//private IWorkbenchPart part;
	Set<String> selectedFiles = new HashSet<String>();
	Set<IResource> selectedResources = new HashSet<IResource>();
	Set<IResource> grayedResources = new HashSet<IResource>();
	Process digProcess = null;
	Thread digThread = null;
	
	class ResourcesPage extends WizardPage implements ITreeContentProvider, ILabelProvider, ICheckStateListener
	{
		private Combo langCombo;
		private CheckboxTreeViewer resourcesTree;
		private ILabelProvider labelProvider;

		public ResourcesPage() {
			super("ResourcesPage");
			setTitle("Please select files to dig");
			labelProvider = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout gl = new GridLayout();
			int ncol = 1;
			gl.numColumns = ncol;
			composite.setLayout(gl);
			new Label(composite, SWT.NONE).setText("Select language:");					
			langCombo = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
			langCombo.add("Python");
			langCombo.add("Java");
			langCombo.select(0);
			langCombo.addSelectionListener(new SelectionListener() 
			{

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					if(resourcesTree != null)
						resourcesTree.refresh();
				}
 
				@Override
				public void widgetSelected(SelectionEvent e) {
					if(resourcesTree != null)
					{
						resourcesTree.refresh();
						resourcesTree.setCheckedElements(selectedResources.toArray());
						resourcesTree.setGrayedElements(grayedResources.toArray());
					}
				}
				
			});
			
			new Label(composite, SWT.NONE).setText("Select files:");
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
			resourcesTree.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
			resourcesTree.refresh();
			resourcesTree.setCheckedElements(selectedResources.toArray());
			resourcesTree.setGrayedElements(grayedResources.toArray());
			
			setControl(composite); 
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if(parentElement instanceof IContainer)
				try {
					return ((IContainer)parentElement).members();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			if(element instanceof IResource)
				((IResource)element).getParent();
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if(element instanceof IContainer)
				try {
					return ((IContainer)element).members().length > 0;
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return false;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			if(inputElement instanceof IContainer)
				try {
					return ((IContainer)inputElement).members();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			return new Object[0];
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public Image getImage(Object element) {
			return labelProvider.getImage(element);
		}

		@Override
		public String getText(Object element) {
			return labelProvider.getText(element);
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
			labelProvider.addListener(listener);
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			return labelProvider.isLabelProperty(element, property);
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
			labelProvider.removeListener(listener);
		}

		@Override
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
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			// create the desired layout for this wizard page
			GridLayout gl = new GridLayout();
			int ncol = 1;
			gl.numColumns = ncol;
			composite.setLayout(gl);
			
			new Label(composite, SWT.NONE).setText("Select language:");
			
			console = new Text(composite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI | SWT.BORDER);
			console.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
			console.setBackground(new Color(null, 0,0,0));
			console.setForeground(new Color(null, 255,255,255));
						
			setControl(composite);
		}		

	}
	
	class DigWizard extends Wizard
	{

		@Override
		public void addPages() {
			super.addPages();
			addPage(new ResourcesPage());
			addPage(new ConsolePage());
			setWindowTitle("Dig clones");
		}

		@Override
		public boolean performFinish() {

			return false;
		}
		
		@Override
		public boolean performCancel() {
			if(digProcess == null) return true;
			digProcess.destroy();
			try {
				digProcess.waitFor();
				digThread.interrupt();
				digThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			digProcess = null;
			digThread = null;
			return true;
		}
	}
	
	/**
	 * The constructor
	 */
	public LabelAction() {
	}

	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
		//IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		//editor.doSave(null);
		
		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		
		if(!PlatformUI.getWorkbench().saveAllEditors(true)) return;
		
		boolean WINDOWS = java.io.File.separatorChar == '\\';
		
		String path = "";
		
		for(String file: selectedFiles) path += "\"" + file + "\" ";
		
		FileDialog dialog = 
			new FileDialog(shell);
		//dialog.open();
		
		//ResourcesPlugin.getWorkspace().getRoot()
		ResourceSelectionDialog dialog2 =
			new ResourceSelectionDialog(shell, ResourcesPlugin.getWorkspace().getRoot(), "msg");
		dialog2.setInitialSelections(selectedResources.toArray());
		//dialog2.open();
		
		ResourceListSelectionDialog dialog3 = 
			new ResourceListSelectionDialog(shell, selectedResources.toArray(new IResource[0]));
		
		//dialog3.open();

		ContainerSelectionDialog dialog4 =
		    new ContainerSelectionDialog(shell, ResourcesPlugin.getWorkspace().getRoot(), true, "msg");
		dialog4.setInitialSelections(selectedResources.toArray());
		//dialog4.open();
		
		DigWizard digWizard = new DigWizard();
		WizardDialog wd = new WizardDialog(shell, digWizard);
		wd.addPageChangedListener(this);
		wd.open();
		
		if(true) return;
		
		/*
		
		IEditorInput editorInput = editor.getEditorInput();
				
		if (editorInput instanceof IURIEditorInput) {
			
			IURIEditorInput uei = (IURIEditorInput)editorInput;
			
			path = uei.getURI().getPath();
			
			if(WINDOWS) path = path.replaceAll("^/+", "");

			//!!!DO NOT use this thing or you will have wrong links in browser!!!
			//path = EFS.getLocalFileSystem().getStore(uei.getURI()).toLocalFile(0, null).getAbsolutePath();
			
			if (path != null) { /**/

		MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "CloneDigger Plug-in", path);

		String tempdir = System.getProperty("java.io.tmpdir");

		if ( !(tempdir.endsWith("/") || tempdir.endsWith("\\")) )
			tempdir = tempdir + System.getProperty("file.separator");

		String htmFile = tempdir + "cde_output.htm";

		String errLog = tempdir + "cde_error.log";

		try {
			Bundle bundle = Platform.getBundle("org.clonedigger");

			(new java.io.File(htmFile)).delete();

			ProcessBuilder pb = new ProcessBuilder();

			if(WINDOWS)
			{
				//cmd /C ""command"  > nul 2>&1"
				pb.command().add("cmd");
				pb.command().add("/C");
				pb.command().add(
						"\"\"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "\\runclonedigger.py\" " +
						"--links-for-eclipse " +
						"--output=\"" + htmFile + "\" " +
						path +
						" > \"" + errLog +"\" 2>&1 \"");
			}
			else
			{
				//sh -c python "..." "..." > /dev/null 2>&1
				pb.command().add("sh");
				pb.command().add("-c");
				pb.command().add(
						"python \"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "/runclonedigger.py\" " +
						"--links-for-eclipse " +
						"--output=\"" + htmFile + "\" " +
						path +
						" > \"" + errLog +"\" 2>&1 ");
			}
			pb.redirectErrorStream(true);

			MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "CloneDigger Plug-in", pb.command().toString());
			System.err.println(pb.command().toString());

			Process proc = pb.start();

			proc.waitFor();
			proc.destroy();

			MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "CloneDigger Plug-in", "end");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "CloneDigger Plug-in", htmFile);

		if((new java.io.File(htmFile)).exists())
			try {

				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

				IEditorInput htmInput = null;
				htmInput = new WebBrowserEditorInput(new URL("file:/" + htmFile.replaceAll("^/+", "")), 0);

				IEditorPart	htmEditor = 
					(IEditorPart)page.openEditor(htmInput,
							"org.clonedigger.resultbrowser");
				//"org.eclipse.ui.browser.editor");		

			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (PartInitException e) {
				e.printStackTrace();
			}

	}

	public void selectResource(IResource res, boolean select)
	{
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
			if(res == null) 
				action.setEnabled(false);
			selectResource(res, true);
		}
	}

	/**
	 * We can use this method to dispose of any system
	 * resources we previously allocated.
	 * @see IWorkbenchWindowActionDelegate#dispose
	 */
	public void dispose() {
	}
	
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		//editor = targetEditor;
	}

	public void init(IViewPart view) {
		//this.view = view;
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		//part = targetPart;		
	}

	@Override
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
					digThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				digProcess = null;
				digThread = null;
			}
			return;
		}
		final ConsolePage consolePage = (ConsolePage) page;
		consolePage.setPageComplete(false);
		
		digThread = new Thread(new Runnable() {
			@Override
			public void run() {
				final byte[] buf = new byte[1024];
				InputStream pi = digProcess.getInputStream();
				while(true)
					try {
						final int len = pi.read(buf);
						if(len < 0) break;
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								consolePage.console.append(new String(buf, 0, len));
							}});
					} catch (IOException e) {
						e.printStackTrace();
					}			
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						consolePage.setPageComplete(true);
					}});				
			}});
		digThread.start();
	}
}