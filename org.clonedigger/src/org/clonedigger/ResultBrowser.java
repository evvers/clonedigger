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

@SuppressWarnings("restriction")
public class ResultBrowser extends WebBrowserEditor {
	
	private class CloneLocation implements LocationListener
	{

		public void changed(LocationEvent event) {}

		public void changing(LocationEvent event) 
		{
			if(event.location.startsWith("clone:"))
			{
				try
				{ 
					String [] args = event.location.split("clone:|\\?|&");

					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					
					IFile file = ResourcesPlugin.getWorkspace().getRoot().
						getFileForLocation(Path.fromOSString(args[1]));
					
					IEditorInput editInput = null;
					
					if(file == null)
					{
						IFileStore fileStore = EFS.getLocalFileSystem().getStore(new URI("file:/" + args[1].replaceAll("^/+", "")));
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
						e.printStackTrace();
					} 					

					event.doit = false;
				}
				catch (PartInitException e) {
					e.printStackTrace();
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}

				/*
				MessageDialog.openInformation(
						null,
						"CloneDigger Plug-in",
						event.location.substring(0, 7)				
					);
				/**/

		}
		
	}
	
	public ResultBrowser() {
		// Auto-generated constructor stub
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		webBrowser.getBrowser().addLocationListener(new CloneLocation());
	}

}
