package org.clonedigger.actions;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.browser.WebBrowserEditorInput;
import org.osgi.framework.Bundle;

/**
 * Our sample action implements workbench action delegate.
 * The action proxy will be created by the workbench and
 * shown in the UI. When the user tries to use the action,
 * this delegate will be created and execution will be 
 * delegated to it.
 * @see IWorkbenchWindowActionDelegate
 */
public class LabelAction implements IEditorActionDelegate {
	private IEditorPart editor; 
	/**
	 * The constructor.
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
		editor.doSave(null);
		String path = null;
		
		IEditorInput editorInput = editor.getEditorInput();
		
		if (editorInput instanceof IURIEditorInput) {
			
			IURIEditorInput uei = (IURIEditorInput)editorInput;
			path = uei.getURI().getPath().substring(1);
			if (path != null) {

				//MessageDialog.openInformation(editor.getSite().getShell(), "CloneDigger Plug-in", path);
				
				String htmFile = System.getProperty("java.io.tmpdir") + "cde_output.htm";
				
				String errLog = System.getProperty("java.io.tmpdir") + "cde_error.log";
				
				try {
					Bundle bundle = Platform.getBundle("org.clonedigger");
					
					boolean WINDOWS = java.io.File.separatorChar == '\\';
					
					(new java.io.File(htmFile)).delete();
					
					ProcessBuilder pb = new ProcessBuilder();
					
					if(WINDOWS)
					{
						//cmd /C "command"  > nul 2>&1
						pb.command().add("cmd");
						pb.command().add("/C");
						pb.command().add(
								"\"\"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "\\runclonedigger.py\" " +
								"--links-for-eclipse " +
								"--output=\"" + htmFile + "\" " +
								"\"" + path + "\"" +
						" > \"" + errLog +"\" 2>&1 \"");
					}
					else
					{
						//sh -c "command"  > /dev/null 2>&1
						pb.command().add("sh");
						pb.command().add("-c");
						pb.command().add(
								"\"\"" + FileLocator.getBundleFile(bundle).getAbsolutePath() + "\\runclonedigger.py\" " +
								"--links-for-eclipse " +
								"--output=\"" + htmFile + "\" " +
								"\"" + path + "\"" +
						" > \"" + errLog +"\" 2>&1 \"");
					}
					pb.redirectErrorStream(true);
					
					Process proc = pb.start();
					
					proc.getOutputStream().write(4);
					proc.getOutputStream().flush();
					
					proc.waitFor();
					proc.destroy();
					
					//MessageDialog.openInformation(editor.getSite().getShell(), "CloneDigger Plug-in", "end");
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//MessageDialog.openInformation(editor.getSite().getShell(), "CloneDigger Plug-in", htmFile);
				
				if((new java.io.File(htmFile)).exists())
				try {

					IWorkbenchPage page = editor.getSite().getWorkbenchWindow().getActivePage();
					
					IEditorInput htmInput = new WebBrowserEditorInput(new URL("file:/" + htmFile), 0);
				
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
	    }

		// This wont work if we edit external file, without creating a project
		/*
		IPathEditorInput fileEditorInput = (IPathEditorInput)(editor.getEditorInput());
		MessageDialog.openInformation(editor.getSite().getShell(),
				"Plug-in test",
				fileEditorInput.getPath().toOSString()				
				);
		*/
	}

	/**
	 * Selection in the workbench has been changed. We 
	 * can change the state of the 'real' action here
	 * if we want, but this can only happen after 
	 * the delegate has been created.
	 * @see IWorkbenchWindowActionDelegate#selectionChanged
	 */
	public void selectionChanged(IAction action, ISelection selection) {
	}

	/**
	 * We can use this method to dispose of any system
	 * resources we previously allocated.
	 * @see IWorkbenchWindowActionDelegate#dispose
	 */
	public void dispose() {
	}
	
	public void setActiveEditor(IAction action, IEditorPart targetEditor)
	{
		editor = targetEditor;
	}
}