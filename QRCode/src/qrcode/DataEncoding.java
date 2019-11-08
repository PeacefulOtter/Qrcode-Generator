package qrcode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import reedsolomon.ErrorCorrectionEncoding;

public final class DataEncoding {

	/**
	 * @param input
	 * @param version
	 * @return
	 */
	public static boolean[] byteModeEncoding(String input, int version) {
		/* QRCode Params */
		// get the maximum array length depending on the version
		int maxInputLength = QRCodeInfos.getMaxInputLength( version );
		int maxCodeWordsLength = QRCodeInfos.getCodeWordsLength( version );
		int eccLength = QRCodeInfos.getECCLength( version );

		// Convert message to integer sequence of given max length using ISO-8859-1
		int[] encodedInput = encodeString( input, maxInputLength );
		// Add encoding and length information
		encodedInput = addInformations( encodedInput );
		// Add padding bytes (236 and 17) to the data until the size of the given array match the given length
		if ( encodedInput.length < maxInputLength )
		{
			encodedInput = fillSequence( encodedInput, maxCodeWordsLength );
		}
		// Add the error correction code to the encodedData
		encodedInput = addErrorCorrection( encodedInput, eccLength );
		// Convert the last 8 bits of each integer in the given array to bit-array (boolean[]). and return that array
		return bytesToBinaryArray( encodedInput );
	}

	/**
	 * @param input
	 *            The string to convert to ISO-8859-1
	 * @param maxLength
	 *          The maximal number of bytes to encode (will depend on the version of the QR code) 
	 * @return A array that represents the input in ISO-8859-1. The output is
	 *         truncated to fit the version capacity
	 */
	public static int[] encodeString(String input, int maxLength) {
		byte[] inputBytes = input.getBytes( StandardCharsets.ISO_8859_1 );

		// prevent the inputBytes from being empty
		if ( inputBytes.length == 0 ) { return new int[] { 0 }; }

		// if the input length is bigger than the maximum space available on the qrcode
		// then we 'cut' the input to fit the maxLength value
		if ( inputBytes.length > maxLength )
		{
			// we store the 'cutted' array in a new one fitting the maxLength value
			byte[] newInputByte = new byte[ maxLength ];
			// and we fill it with the inputBytes values
			for ( int i = 0; i < maxLength; ++i )
			{
				newInputByte[ i ] = inputBytes[ i ];
			}
			// finally, we set the inputBytes as the 'cutted' array
			inputBytes = newInputByte;
		}
		// since the function returns an array made up of integers
		// we need to convert every bit to the corresponding value :
		//   false -> 0, true -> 1
		return convertUnsignedInt( inputBytes );
	}

	/**
	 *
	 * @param byteArray : The array made up of bytes
	 * @return Integers array with the corresponding value of each bit in the byteArray
	 */
	public static int[] convertUnsignedInt(byte[] byteArray) {
		int[] intArray = new int[ byteArray.length ];
		for ( int i = 0; i < byteArray.length; i++ ) {
			intArray[ i ] = byteArray[ i ] & 0xFF;
		}
		return intArray;
	}

	/**
	 * Add the 12 bits information data and concatenate the bytes to it
	 * 
	 * @param inputBytes
	 *            the data byte sequence
	 * @return The input bytes with an header giving the type and size of the data
	 */
	public static int[] addInformations( int[] inputBytes ) {
		byte[] infoInputArray = new byte[ inputBytes.length + 2 ];
		int bytesLength = inputBytes.length;

		// Prefix the message with the 4-bit encoding information of byte mode: 0100 -> 0b0100 that we shift to the left 4 times => 0100_0000
		// And add the four firsts bit to encode the length of the message 0000
		infoInputArray[ 0 ] = (byte) (  ( 0b0100 << 4 )  |  ( bytesLength >> 4 ) );

		// Add the last 4 bits to encode the length of the message 1110_ + the four first bits of the inputBytes
		infoInputArray[ 1 ] = (byte) (  ( (byte) ( bytesLength << 4 ) )  |  (byte) ( inputBytes[ 0 ] >> 4 )  );

		for ( int i = 0; i < inputBytes.length; i++ ) {
			if ( i < inputBytes.length - 1 ) {
				infoInputArray[ i+2 ] = (byte) ( inputBytes[ i ] << 4 | inputBytes[ i + 1 ] >> 4 );
			} else {
				infoInputArray[ i+2 ] = (byte) ( inputBytes[ i ] << 4 );
			}
		}
		return convertUnsignedInt( infoInputArray );
	}

	/**
	 * Add padding bytes to the data until the size of the given array matches the
	 * finalLength
	 * 
	 * @param encodedData
	 *            the initial sequence of bytes
	 * @param finalLength
	 *            the minimum length of the returned array
	 * @return an array of length max(finalLength,encodedData.length) padded with
	 *         bytes 236,17
	 */
	public static int[] fillSequence(int[] encodedData, int finalLength) {
		int[] fillDataArray = new int[ finalLength ];
		boolean swap = true;
		for ( int i = 0; i < finalLength; i++ ) {
			int dummy = 0;
			// encodedData at index i is defined
			if ( i < encodedData.length ) {
				 dummy = encodedData[ i ];
			// the index i is bigger than the encodedData array length
			// so we fill the array with 236 and 17
			} else {
				if ( swap ) { dummy = 236; }
				else if ( !swap ) { dummy = 17; }
				swap = !swap;
			}
			fillDataArray[ i ] = dummy;
		}
		return fillDataArray;
	}

	/**
	 * Add the error correction to the encodedData
	 * 
	 * @param encodedData
	 *            The byte array representing the data encoded
	 * @param eccLength
	 *            the version of the QR code
	 * @return the original data concatenated with the error correction
	 */
	public static int[] addErrorCorrection(int[] encodedData, int eccLength) {
		int arrayLength = encodedData.length + eccLength;
		int[] errorCorrectionArray = new int[ arrayLength ];
		int[] errorData = ErrorCorrectionEncoding.encode( encodedData, eccLength );
		for ( int i = 0; i < arrayLength; i++ ) {
			if ( i < encodedData.length ) {
				errorCorrectionArray[ i ] = encodedData[ i ];
			} else {
				errorCorrectionArray[ i ] = errorData[ i - encodedData.length ];
			}
		}
		return errorCorrectionArray;
	}

	/**
	 * Encode the byte array into a binary array represented with boolean using the
	 * most significant bit first.
	 * 
	 * @param data
	 *            an array of bytes
	 * @return a boolean array representing the data in binary
	 */
	public static boolean[] bytesToBinaryArray(int[] data) {
		boolean[] boolArray = new boolean[ data.length * 8 ];

		for ( int i = 0; i < data.length; i++ ) {
			int b = data[ i ];
			// each byte is made of 8 bit
			for ( int j = 0; j < 8; j++ ) {
				// Explanation :
				// 		take each byte separately and shift it to the right (7-j)
				//      and get the most right bit after the shifting
				//      since we actually want a boolean value we use the (bit == 1)
				// 		trick, and get a boolean
				//      we finally store the boolean value at the
				//      (i*8)+j index of boolArray in order to cover the whole array length
				int index = ( i * 8 ) + j;
				int shifting = ( b >> ( 7 - j ) ) & 0x01;
				boolArray[ index ] = shifting == 1;
			}
		}
		return boolArray;
	}

}
