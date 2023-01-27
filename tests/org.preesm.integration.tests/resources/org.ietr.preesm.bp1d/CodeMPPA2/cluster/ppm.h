/*
	============================================================================
	Name        : ppm.h
	Author      : kdesnos
	Version     : 1.0
	Copyright   : CECILL-C, IETR, INSA Rennes
	Description : Actor code to read/write a ppm file from the hard drive
	============================================================================
*/

#ifndef READ_PPM_H
#define READ_PPM_H

/**
* This function initialize the reading of a file on the hard drive.
* The method opens the file with the given id and check that its heights
* and width are consistent with the one provided to the function.
* 
* @param id
*        Index of the image to read. Each index corresponds to a path defined
*        in the ppm.c file.
* @param height
*        The expected height of the read image.
* @param width
*        The expected width of the read image.
*/
void readPPMInit(int id, int height, int width, char* paths[]);

/**
* Read the content of a PPM file from the hard drive.
* A call to readPPMInit() is mandatory for each file ID before calling
* readPPM() for this file ID.
*
* @param id
*        Index of the image to read. Each index corresponds to a path defined
*        in the ppm.c file.
* @param height
*        The height of the read image.
* @param width
*        The width of the read image.
* @param r
*        Output array of size height*width for the red component
* @param g
*        Output array of size height*width for the green component
* @param b
*        Output array of size height*width for the blue component
*/
//void readPPM(int id,int height, int width, unsigned char *r, unsigned char *g, unsigned char *b);
void readPPM(int height, int width, unsigned char *rgbLeft, unsigned char *rgbRight);

/**
* Write a gray image into a file.
* @param height
*        The height of the written image.
* @param width
*        The width of the written image.
* @param gray
*        Height*width pixels to write.
*/
//void writePPM(int height, int width, unsigned char *gray, char* paths);
void writePPM(int height, int width, unsigned char *gray);

#endif
