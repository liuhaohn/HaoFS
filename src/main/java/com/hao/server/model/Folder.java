package com.hao.server.model;

public class Folder
{
    private String folderId;
    private String folderName;
    private String folderCreationDate;
    private String folderCreator;
    private String folderParent;
    private int folderConstraint;

    public Folder() {
    }

    public Folder(String folderId, String folderName, String folderCreationDate, String folderCreator, String folderParent, int folderConstraint) {
        this.folderId = folderId;
        this.folderName = folderName;
        this.folderCreationDate = folderCreationDate;
        this.folderCreator = folderCreator;
        this.folderParent = folderParent;
        this.folderConstraint = folderConstraint;
    }

    public String getFolderId() {
        return this.folderId;
    }
    
    public void setFolderId(final String folderId) {
        this.folderId = folderId;
    }
    
    public String getFolderName() {
        return this.folderName;
    }
    
    public void setFolderName(final String folderName) {
        this.folderName = folderName;
    }
    
    public String getFolderCreationDate() {
        return this.folderCreationDate;
    }
    
    public void setFolderCreationDate(final String folderCreationDate) {
        this.folderCreationDate = folderCreationDate;
    }
    
    public String getFolderCreator() {
        return this.folderCreator;
    }
    
    public void setFolderCreator(final String folderCreator) {
        this.folderCreator = folderCreator;
    }
    
    public String getFolderParent() {
        return this.folderParent;
    }
    
    public void setFolderParent(final String folderParent) {
        this.folderParent = folderParent;
    }

	public int getFolderConstraint() {
		return folderConstraint;
	}

	public void setFolderConstraint(int folderConstraint) {
		this.folderConstraint = folderConstraint;
	}
}
