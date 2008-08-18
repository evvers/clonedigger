package org.clonedigger;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.browser.WebBrowserEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Implementation of WebBrowser with support for "clone:" links. 
 * Make a hook on location change event and navigate to editor.  
 */
@SuppressWarnings("restriction")
public class ResultBrowser extends WebBrowserEditor {
	
	private class CloneLocation implements LocationListener
	{

		public void changed(LocationEvent event) {}

		public void changing(LocationEvent event) 
		{
			boolean WINDOWS = java.io.File.separatorChar == '\\';
			if(event.location.startsWith("clone:"))
			{
				event.doit = false;
				try
				{ 
					String [] args = event.location.split("clone://|\\?|&");
					
					//patch on strange browsersupport behavior on links with "\" character
					args[1] = args[1].replaceAll("/+$", "");
					if(WINDOWS) args[1] = args[1].replaceAll("/", "\\\\");
					
					IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
					
					IFile file = ResourcesPlugin.getWorkspace().getRoot().
						getFileForLocation(Path.fromOSString(args[1])); 
					
					IEditorInput editInput = null;
					
					if(file == null)
					{
						// Process external files, files that arent present in workspace for some reasons.
						IFileStore fileStore = EFS.getLocalFileSystem().getStore(
								new URI("file:/" + args[1].replaceAll("^/+", "").replaceAll("\\\\", "/")));
						editInput = new FileStoreEditorInput(fileStore);
					}
					else
					{
						editInput = new FileEditorInput(file);
					}

					ITextEditor editor = 
						(ITextEditor)IDE.openEditor(page, editInput,
								IDE.getEditorDescriptor(args[1]).getId(), true);
								//"org.python.pydev.editor.PythonEditor", true);
					IDocument doc = editor.getDocumentProvider().getDocument(editInput);

					try
					{
						int start = doc.getLineInformation(Integer.parseInt(args[2])).getOffset();
						int end = doc.getLineInformation(Integer.parseInt(args[3]) + 1).getOffset();
						editor.setHighlightRange(start, end-start, true);
					}
					catch (BadLocationException e) {
						Activator.log(e);
					} 					
				}
				catch (PartInitException e) {
					Activator.log(e);
				} catch (URISyntaxException e) {
					Activator.log(e);
				}
			}
		}
	}
	
	public ResultBrowser() {
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		webBrowser.getBrowser().addLocationListener(new CloneLocation());
	}

}
