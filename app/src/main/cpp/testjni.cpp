//
// Created by iris.lin on 2018/1/9.
//
#include <clocale>
#include <stdint.h>
#include <malloc.h>
#include <stdlib.h>
#include <string.h>
#include "testjni.h"
#include "dlpspec_types.h"
#include "dlpspec_setup.h"
#include "dlpspec_util.h"
#include "dlpspec_calib.h"
#include "dlpspec_scan_col.h"
#include "dlpspec_scan_had.h"

/**
 * Calculates the Maximum number of patterns possible
 * based on the start & end wavelengths ,calibration coefficients, scan type and width
 * calculation done seperately for column and hadamard types
 * @param startnm - I - the start wavelength for that section
 * @param endnm - I - the end wavelength for that section
 * @param widthIndex - I - the width for that section
 * @param scan_type - I - Column(0) or Hadamard(1) type
 * @return the Maximum allowed patterns for the section with given inputs
 */
int GetMaxPattern(int scan_type,int startnm,int endnm, int widthIndex, int numRepeat, char *SpectrumCalCoefficients)
{
    double startCol, endCol;
    int maxPatterns = 0;
    int maxShift = 0; 			// maximum shift across all rows of the DMD
    int blockShift = 0;			// maximum shift in a 4-row block
    int overlap = 0, maxOverlap = 0;			// amount of overlay between consecutive patterns
    calibCoeffs m_calibCoeffs;
    int8_t* shiftVector = NULL;
    DLPSPEC_ERR_CODE ret_val = DLPSPEC_PASS;

    if((startnm < MIN_WAVELENGTH_NM) || (endnm > MAX_WAVELENGTH_NM) || (startnm > endnm))
        return 0;
    char byteData[sizeof(calibCoeffs)*3] = "";
    memcpy(byteData,SpectrumCalCoefficients, sizeof(calibCoeffs)*3);
    dlpspec_calib_read_data(&byteData, sizeof(calibCoeffs)*3);
    memcpy(&m_calibCoeffs, &byteData, sizeof(calibCoeffs));


    dlpspec_util_nmToColumn(startnm, m_calibCoeffs.PixelToWavelengthCoeffs, &endCol);
    dlpspec_util_nmToColumn(endnm, m_calibCoeffs.PixelToWavelengthCoeffs, &startCol);

    shiftVector = (int8_t*)(malloc(sizeof(uint8_t) * DMD_HEIGHT));
    dlpspec_calib_genShiftVector(m_calibCoeffs.ShiftVectorCoeffs,
                                 DMD_HEIGHT, shiftVector);
    for (int k = 0; k < DMD_HEIGHT; k+= 4)
    {
        for (int line = 1; line < 4; line++ )
        {
            if ( abs(shiftVector[line+k-1] - shiftVector[line+k]) > blockShift )
                blockShift = abs(shiftVector[line+k-1] - shiftVector[line+k]);
        }
        if ( blockShift > maxShift )
            maxShift = blockShift;
        blockShift = 0;
    }
    if(shiftVector != NULL)
        free(shiftVector);
    if(scan_type == COLUMN_TYPE)
    {
        patDefCol patDefC;
        bool pack8 = false;
        scanConfig new_config;
        new_config.wavelength_start_nm = startnm;
        new_config.wavelength_end_nm = endnm;
        new_config.width_px = widthIndex;
        new_config.num_repeats = numRepeat;
        new_config.scan_type =  COLUMN_TYPE;

        maxOverlap = 0;

        for(int i=8; i<=(MAX_PATTERNS_PER_SCAN); i++)
        {
            new_config.num_patterns = i;
            dlpspec_scan_col_genPatDef(&new_config,&m_calibCoeffs,&patDefC);
            for(int j=0; j<(patDefC.numPatterns - 8); j++) {
                overlap = patDefC.colWidth - abs(patDefC.colMidPix[j + 1] - patDefC.colMidPix[j]);
                if (overlap > maxOverlap)
                    maxOverlap = overlap;
                if (abs(patDefC.colMidPix[j + 7] - patDefC.colMidPix[j]) >
                    (7 + maxShift + maxOverlap + 1))
                    pack8 = false;
                else {
                    pack8 = true;       // More than 8 colors between pattern midpoints
                    break;
                }
            }

            if( (i <= abs(patDefC.colMidPix[patDefC.numPatterns - 1] - patDefC.colMidPix[0])) && !pack8)
            {
                if((pack8 && (patDefC.colWidth <= 16)) || !pack8)
                    maxPatterns = i;
                else
                    break;
            }
            else
                break;

        }
    }

    else if(scan_type == HADAMARD_TYPE)
    {
        patDefHad patDefH;

        scanConfig new_config;
        new_config.wavelength_start_nm = startnm;
        new_config.wavelength_end_nm = endnm;
        new_config.width_px = widthIndex;
        new_config.num_repeats = numRepeat;
        new_config.scan_type =  HADAMARD_TYPE;

        for(int i=1; i<MAX_PATTERNS_PER_SCAN; i++)
        {
            new_config.num_patterns = i;
            ret_val = dlpspec_scan_had_genPatDef(&new_config,&m_calibCoeffs,&patDefH);
            // valid pattern generation && unique non-repeated pattern && patterns do not exceed memory && patterns do not exceed ADC buffer
            if((ret_val == DLPSPEC_PASS) && (i <= (endCol - startCol)) && (patDefH.numPatterns < MAX_PATTERNS_PER_SCAN) && (patDefH.numPatterns < (ADC_DATA_LEN - MAX_PATTERNS_PER_SCAN/24)))
                maxPatterns = new_config.num_patterns;
        }
    }
    return maxPatterns;
}