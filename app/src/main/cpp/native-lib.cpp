#include <jni.h>
#include <string>
#include <string.h>
#include "testjni.h"
#include "dlpspec_scan.h"


/*JNIEXPORT jint

JNICALL
Java_com_testlibraryappcompany_myapplication_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject,jfloatArray javaFloat,jint  javaInt/* this *//*) {
    std::string hello = "Hello from C++";
    float *javaFloatArray = env->GetFloatArrayElements(javaFloat,NULL);
    jint aaa =  changeFloatByCpp(javaFloatArray,javaInt);
    //jint aaa = 100;

    return aaa;
}*/
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
extern "C"
JNIEXPORT jint

JNICALL
Java_com_isctechnologies_NanoScan_NewScanActivity_GetMaxPatternJNI(
        JNIEnv *env,
        jobject,jint scan_type,jint start_nm,jint end_nm, jint width_index, jint num_repeat,jbyteArray SpectrumCalCoefficients/* this */) {

    int len = env->GetArrayLength (SpectrumCalCoefficients);
    char* SpectrumCalCoefficientsCharArray = new  char[len];
    env->GetByteArrayRegion (SpectrumCalCoefficients, 0, len, reinterpret_cast<jbyte*>(SpectrumCalCoefficientsCharArray));
    //--------------------------------------------------------------------------------------
   // char* SpectrumCalCoefficientsCharArray = (char*)env->GetByteArrayElements(SpectrumCalCoefficients, 0);
    jint maxPatterns =  GetMaxPattern(scan_type,start_nm, end_nm,width_index, num_repeat,SpectrumCalCoefficientsCharArray);

    return maxPatterns;
}
/**
 * Function to interpret reference scan data into what the reference scan would have been
 * if it were scanned with the configuration which @p scanData was scanned with.
 * This can be used to compute a reference for an arbitrary scan taken when a physical
 * reflective reference is not available to take a new reference measurement.
 *
 * @param[in]   CalCoefficients  	            Pointer to serialized reference calibration data
 * @param[in]   RefCalMatrix                    Pointer to serialized reference calibration matrix
 * @param[in]   scanData                        Scan results from sample scan data (output of dlpspec_scan_interpret function)
 * @param[out]  wavelength                      Pointer to scan data result wavelength
 * @param[out]  intensity                       Pointer to scan data result intensity
 * @param[out]  uncalibratedIntensity           Pointer to scan data result uncalibratedIntensity
 *
 * @return       scan data result length
 *
 */
extern "C"
JNIEXPORT jint

JNICALL
Java_com_isctechnologies_NanoScan_NewScanActivity_dlpSpecScanInterpReference(
        JNIEnv *env,
        jobject ,jbyteArray scanData, jbyteArray CalCoefficients,jbyteArray RefCalMatrix ,
        jdoubleArray wavelength,jintArray intensity,jintArray uncalibratedIntensity/* this */) {
    const char tpl_header[] = "tpl\0"; // I need to replace the first three bytes to magic number "tpl" to avoid interpReference error of format signature mismatch
    scanResults finalScanResults,referenceResults;
   //transfer jbytearray to char array--------------------------------------------------------------------------------------------------------
    int scanData_len = env->GetArrayLength (scanData);
    char* scanDataCharArray = new  char[scanData_len];
    env->GetByteArrayRegion (scanData, 0, scanData_len, reinterpret_cast<jbyte*>(scanDataCharArray));
    //memcpy(scanDataCharArray, tpl_header, sizeof(tpl_header));

    int CalCoefficients_len = env->GetArrayLength (CalCoefficients);
    char* CalCoefficientsCharArray = new  char[CalCoefficients_len];
    env->GetByteArrayRegion (CalCoefficients, 0, CalCoefficients_len, reinterpret_cast<jbyte*>(CalCoefficientsCharArray));
    memcpy(CalCoefficientsCharArray, tpl_header, sizeof(tpl_header));

    int RefCalMatrix_len = env->GetArrayLength (RefCalMatrix);
    char* RefCalMatrixCharArray = new  char[RefCalMatrix_len];
    env->GetByteArrayRegion (RefCalMatrix, 0, RefCalMatrix_len, reinterpret_cast<jbyte*>(RefCalMatrixCharArray));
    memcpy(RefCalMatrixCharArray, tpl_header, sizeof(tpl_header));


    dlpspec_scan_interpret(scanDataCharArray,scanData_len,&finalScanResults);//interpret scanData some info and uncalibratedIntensity value
    dlpspec_scan_interpReference(CalCoefficientsCharArray,CalCoefficients_len,RefCalMatrixCharArray,RefCalMatrix_len,&finalScanResults,&referenceResults);

    //copy interpret value to java parameter ---------------------------------------------------------------------------------------------------------------------
    int *javaintensityArray = env->GetIntArrayElements(intensity,NULL);
    for(int i=0;i<referenceResults.length;i++)
    {
        javaintensityArray[i] = referenceResults.intensity[i];
    }
    env->ReleaseIntArrayElements(intensity,javaintensityArray,0);//should release ,otherwise back to java code,the value can not get

    int *javauncalibratedIntensityArray = env->GetIntArrayElements(uncalibratedIntensity,NULL);
    for(int i=0;i<referenceResults.length;i++)
    {
        javauncalibratedIntensityArray[i] = finalScanResults.intensity[i];
    }
    env->ReleaseIntArrayElements(uncalibratedIntensity,javauncalibratedIntensityArray,0);

    double *javawavelengthArray = env->GetDoubleArrayElements(wavelength,NULL);
    for(int i=0;i<referenceResults.length;i++)
    {
        javawavelengthArray[i] = finalScanResults.wavelength[i];
    }
    env->ReleaseDoubleArrayElements(wavelength,javawavelengthArray,0);

    int length = referenceResults.length;
    return length;
}

extern "C"
JNIEXPORT int

JNICALL
Java_com_isctechnologies_NanoScan_NewScanActivity_dlpSpecScanReadConfiguration(
        JNIEnv *env,
        jobject,jbyteArray ConfigData,jintArray scanType,jintArray scanConfigIndex,jbyteArray scanConfigSerialNumber,jbyteArray configName,jbyteArray numSections,
        jbyteArray sectionScanType, jbyteArray sectionWidthPx, jintArray sectionWavelengthStartNm, jintArray sectionWavelengthEndNm, jintArray sectionNumPatterns,
        jintArray sectionNumRepeats, jintArray sectionExposureTime/* this */) {

    int len = env->GetArrayLength (ConfigData);
    char* ConfigDataCharArray = new  char[len];
    env->GetByteArrayRegion (ConfigData, 0, len, reinterpret_cast<jbyte*>(ConfigDataCharArray));
    dlpspec_scan_read_configuration(ConfigDataCharArray,len);
    uScanConfig *aScanConfig = (uScanConfig *)ConfigDataCharArray;

    //scanType--------------------------------------------------------------------------------------
    int scanType_len = env->GetArrayLength (scanType);
    int* scanTypeIntArray = new  int[scanType_len];
    env->GetIntArrayRegion (scanType, 0, scanType_len,scanTypeIntArray);
    scanTypeIntArray[0] = aScanConfig->scanCfg.scan_type;
    env->ReleaseIntArrayElements(scanType,scanTypeIntArray,0);
    //scanConfigIndex--------------------------------------------------------------------------------------
    int scanConfigIndex_len = env->GetArrayLength (scanConfigIndex);
    int* scanConfigIndexIntArray = new  int[scanConfigIndex_len];
    env->GetIntArrayRegion (scanConfigIndex, 0, scanConfigIndex_len,scanConfigIndexIntArray);
    scanConfigIndexIntArray[0] = aScanConfig->scanCfg.scanConfigIndex;
    env->ReleaseIntArrayElements(scanConfigIndex,scanConfigIndexIntArray,0);
    //scanConfigSerialNumber--------------------------------------------------------------------------------------
    int scanConfigSerialNumber_len = env->GetArrayLength (scanConfigSerialNumber);
    char* scanConfigSerialNumberCharArray = new  char[scanConfigSerialNumber_len];
    env->GetByteArrayRegion (scanConfigSerialNumber, 0, scanConfigSerialNumber_len, reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray));
    for(int i=0;i<8;i++)
    {
        scanConfigSerialNumberCharArray[i] = aScanConfig->scanCfg.ScanConfig_serial_number[i];
    }
    env->ReleaseByteArrayElements(scanConfigSerialNumber,reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray),0);
    //configName--------------------------------------------------------------------------------------
    int configName_len = env->GetArrayLength (configName);
    char* configNameCharArray = new  char[configName_len];
    env->GetByteArrayRegion (configName, 0, configName_len, reinterpret_cast<jbyte*>(configNameCharArray));
    for(int i=0;i<40;i++)
    {
        configNameCharArray[i] = aScanConfig->scanCfg.config_name[i];
    }
    env->ReleaseByteArrayElements(configName,reinterpret_cast<jbyte*>(configNameCharArray),0);
    //numSections--------------------------------------------------------------------------------------
    int numSections_len = env->GetArrayLength (numSections);
    char* numSectionsCharArray = new  char[numSections_len];
    env->GetByteArrayRegion (numSections, 0, numSections_len, reinterpret_cast<jbyte*>(numSectionsCharArray));
    numSectionsCharArray[0] = aScanConfig->slewScanCfg.head.num_sections;
    env->ReleaseByteArrayElements(numSections,reinterpret_cast<jbyte*>(numSectionsCharArray),0);
    //sectionScanType--------------------------------------------------------------------------------------------------------------
    int sectionScanType_len = env->GetArrayLength (sectionScanType);
    char* sectionScanTypeCharArray = new  char[sectionScanType_len];
    env->GetByteArrayRegion (sectionScanType, 0, sectionScanType_len, reinterpret_cast<jbyte*>(sectionScanTypeCharArray));
    int section = aScanConfig->slewScanCfg.head.num_sections;
    for(int i=0;i<section;i++)
    {
        sectionScanTypeCharArray[i] = aScanConfig->slewScanCfg.section[i].section_scan_type;
    }
    env->ReleaseByteArrayElements(sectionScanType,reinterpret_cast<jbyte*>(sectionScanTypeCharArray),0);
    //sectionWidthPx--------------------------------------------------------------------------------------------------------------
    int sectionWidthPx_len = env->GetArrayLength (sectionWidthPx);
    char* sectionWidthPxCharArray = new  char[sectionWidthPx_len];
    env->GetByteArrayRegion (sectionWidthPx, 0, sectionWidthPx_len, reinterpret_cast<jbyte*>(sectionWidthPxCharArray));
    for(int i=0;i<section;i++)
    {
        sectionWidthPxCharArray[i] = aScanConfig->slewScanCfg.section[i].width_px;
    }
    env->ReleaseByteArrayElements(sectionWidthPx,reinterpret_cast<jbyte*>(sectionWidthPxCharArray),0);
    //sectionWavelengthStartNm--------------------------------------------------------------------------------------------------------------
    int sectionWavelengthStartNm_len = env->GetArrayLength (sectionWavelengthStartNm);
    int * sectionWavelengthStartNmIntArray = new  int[sectionWidthPx_len];
    env->GetIntArrayRegion (sectionWavelengthStartNm, 0, sectionWavelengthStartNm_len, sectionWavelengthStartNmIntArray);
    for(int i=0;i<section;i++)
    {
        sectionWavelengthStartNmIntArray[i] = aScanConfig->slewScanCfg.section[i].wavelength_start_nm;
    }
    env->ReleaseIntArrayElements(sectionWavelengthStartNm,sectionWavelengthStartNmIntArray,0);
    //sectionWavelengthEndNm--------------------------------------------------------------------------------------------------------------
    int sectionWavelengthEndNm_len = env->GetArrayLength (sectionWavelengthEndNm);
    int * sectionWavelengthEndNmIntArray = new  int[sectionWavelengthEndNm_len];
    env->GetIntArrayRegion (sectionWavelengthEndNm, 0, sectionWavelengthEndNm_len, sectionWavelengthEndNmIntArray);
    for(int i=0;i<section;i++)
    {
        sectionWavelengthEndNmIntArray[i] = aScanConfig->slewScanCfg.section[i].wavelength_end_nm;
    }
    env->ReleaseIntArrayElements(sectionWavelengthEndNm,sectionWavelengthEndNmIntArray,0);
    //sectionNumPatterns--------------------------------------------------------------------------------------------------------------
    int sectionNumPatterns_len = env->GetArrayLength (sectionNumPatterns);
    int * sectionNumPatternsIntArray = new  int[sectionNumPatterns_len];
    env->GetIntArrayRegion (sectionNumPatterns, 0, sectionNumPatterns_len, sectionNumPatternsIntArray);
    for(int i=0;i<section;i++)
    {
        sectionNumPatternsIntArray[i] = aScanConfig->slewScanCfg.section[i].num_patterns;
    }
    env->ReleaseIntArrayElements(sectionNumPatterns,sectionNumPatternsIntArray,0);
    //sectionNumRepeats--------------------------------------------------------------------------------------------------------------
    int sectionNumRepeats_len = env->GetArrayLength (sectionNumRepeats);
    int * sectionNumRepeatsIntArray = new  int[sectionNumRepeats_len];
    env->GetIntArrayRegion (sectionNumRepeats, 0, sectionNumRepeats_len, sectionNumRepeatsIntArray);
    for(int i=0;i<section;i++)
    {
        sectionNumRepeatsIntArray[i] = aScanConfig->slewScanCfg.head.num_repeats;
    }
    env->ReleaseIntArrayElements(sectionNumRepeats,sectionNumRepeatsIntArray,0);
    //sectionExposureTime--------------------------------------------------------------------------------------------------------------
    int sectionExposureTime_len = env->GetArrayLength (sectionExposureTime);
    int * sectionExposureTimeIntArray = new  int[sectionExposureTime_len];
    env->GetIntArrayRegion (sectionExposureTime, 0, sectionExposureTime_len, sectionExposureTimeIntArray);
    for(int i=0;i<section;i++)
    {
        sectionExposureTimeIntArray[i] =  aScanConfig->slewScanCfg.section[i].exposure_time;
    }
    env->ReleaseIntArrayElements(sectionExposureTime,sectionExposureTimeIntArray,0);
    int ret = 1;
    return ret;
}

extern "C"
JNIEXPORT int

JNICALL
Java_com_isctechnologies_NanoScan_NewScanActivity_dlpSpecScanReadOneSectionConfiguration(
        JNIEnv *env,
        jobject,jbyteArray ConfigData,jintArray scanType,
        jbyteArray sectionScanType, jbyteArray WidthPx, jintArray WavelengthStartNm, jintArray WavelengthEndNm, jintArray NumPatterns,
        jintArray NumRepeats, jintArray ExposureTime,jintArray scanConfigIndex,jbyteArray scanConfigSerialNumber,jbyteArray configName/* this */) {

    int len = env->GetArrayLength (ConfigData);
    char* ConfigDataCharArray = new  char[len];
    env->GetByteArrayRegion (ConfigData, 0, len, reinterpret_cast<jbyte*>(ConfigDataCharArray));
    dlpspec_scan_read_configuration(ConfigDataCharArray,len);
    uScanConfig *aScanConfig = (uScanConfig *)ConfigDataCharArray;

    //scanType--------------------------------------------------------------------------------------
    int scanType_len = env->GetArrayLength (scanType);
    int* scanTypeIntArray = new  int[scanType_len];
    env->GetIntArrayRegion (scanType, 0, scanType_len,scanTypeIntArray);
    scanTypeIntArray[0] = aScanConfig->scanCfg.scan_type;
    env->ReleaseIntArrayElements(scanType,scanTypeIntArray,0);
    //startnum--------------------------------------------------------------------------------------
    int WavelengthStartNm_len = env->GetArrayLength (WavelengthStartNm);
    int* WavelengthStartNmIntArray = new  int[WavelengthStartNm_len];
    env->GetIntArrayRegion (WavelengthStartNm, 0, WavelengthStartNm_len,WavelengthStartNmIntArray);
    WavelengthStartNmIntArray[0] = aScanConfig->scanCfg.wavelength_start_nm;
    env->ReleaseIntArrayElements(WavelengthStartNm,WavelengthStartNmIntArray,0);
    //endnum--------------------------------------------------------------------------------------
    int WavelengthEndNm_len = env->GetArrayLength (WavelengthEndNm);
    int* WavelengthEndNmIntArray = new  int[WavelengthEndNm_len];
    env->GetIntArrayRegion (WavelengthEndNm, 0, WavelengthEndNm_len,WavelengthEndNmIntArray);
    WavelengthEndNmIntArray[0] = aScanConfig->scanCfg.wavelength_end_nm;
    env->ReleaseIntArrayElements(WavelengthEndNm,WavelengthEndNmIntArray,0);
    //width px--------------------------------------------------------------------------------------
    int WidthPx_len = env->GetArrayLength (WidthPx);
    char* WidthPxCharArray = new  char[WidthPx_len];
    env->GetByteArrayRegion (WidthPx, 0, WidthPx_len,reinterpret_cast<jbyte*>(WidthPxCharArray));
    WidthPxCharArray[0] = aScanConfig->scanCfg.width_px;
    env->ReleaseByteArrayElements(WidthPx,reinterpret_cast<jbyte*>(WidthPxCharArray),0);
    //number of pattern--------------------------------------------------------------------------------------
    int NumPatterns_len = env->GetArrayLength (NumPatterns);
    int* NumPatternsIntArray = new  int[NumPatterns_len];
    env->GetIntArrayRegion (NumPatterns, 0, NumPatterns_len,NumPatternsIntArray);
    NumPatternsIntArray[0] = aScanConfig->scanCfg.num_patterns;
    env->ReleaseIntArrayElements(NumPatterns,NumPatternsIntArray,0);
    //number of repeat--------------------------------------------------------------------------------------
    int NumRepeats_len = env->GetArrayLength (NumRepeats);
    int* NumRepeatsIntArray = new  int[NumRepeats_len];
    env->GetIntArrayRegion (NumRepeats, 0,NumRepeats_len,NumRepeatsIntArray);
    NumRepeatsIntArray[0] = aScanConfig->scanCfg.num_repeats;
    env->ReleaseIntArrayElements(NumRepeats,NumRepeatsIntArray,0);
    //exposure time--------------------------------------------------------------------------------------
    int ExposureTime_len = env->GetArrayLength (ExposureTime);
    int* ExposureTimeIntArray = new  int[ExposureTime_len];
    env->GetIntArrayRegion (ExposureTime, 0, ExposureTime_len,ExposureTimeIntArray);
    ExposureTimeIntArray[0] = aScanConfig->slewScanCfg.section[0].exposure_time;
    env->ReleaseIntArrayElements(ExposureTime,ExposureTimeIntArray,0);
    //scanConfigIndex--------------------------------------------------------------------------------------
    int scanConfigIndex_len = env->GetArrayLength (scanConfigIndex);
    int* scanConfigIndexIntArray = new  int[scanConfigIndex_len];
    env->GetIntArrayRegion (scanConfigIndex, 0, scanConfigIndex_len,scanConfigIndexIntArray);
    scanConfigIndexIntArray[0] = aScanConfig->scanCfg.scanConfigIndex;
    env->ReleaseIntArrayElements(scanConfigIndex,scanConfigIndexIntArray,0);
    //scanConfigSerialNumber--------------------------------------------------------------------------------------
    int scanConfigSerialNumber_len = env->GetArrayLength (scanConfigSerialNumber);
    char* scanConfigSerialNumberCharArray = new  char[scanConfigSerialNumber_len];
    env->GetByteArrayRegion (scanConfigSerialNumber, 0, scanConfigSerialNumber_len, reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray));
    for(int i=0;i<8;i++)
    {
        scanConfigSerialNumberCharArray[i] = aScanConfig->scanCfg.ScanConfig_serial_number[i];
    }
    env->ReleaseByteArrayElements(scanConfigSerialNumber,reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray),0);
    //configName--------------------------------------------------------------------------------------
    int configName_len = env->GetArrayLength (configName);
    char* configNameCharArray = new  char[configName_len];
    env->GetByteArrayRegion (configName, 0, configName_len, reinterpret_cast<jbyte*>(configNameCharArray));
    for(int i=0;i<40;i++)
    {
        configNameCharArray[i] = aScanConfig->scanCfg.config_name[i];
    }
    env->ReleaseByteArrayElements(configName,reinterpret_cast<jbyte*>(configNameCharArray),0);

    int ret = 1;
    return ret;
}
extern "C"
JNIEXPORT int

JNICALL
Java_com_isctechnologies_NanoScan_NewScanActivity_dlpSpecScanWriteConfiguration(
        JNIEnv *env,
        jobject,jint scanType,jint scanConfigIndex,jint numRepeat,jbyteArray scanConfigSerialNumber,jbyteArray configName,jbyte numSections,
        jbyteArray sectionScanType, jbyteArray sectionWidthPx, jintArray sectionWavelengthStartNm, jintArray sectionWavelengthEndNm, jintArray sectionNumPatterns
        , jintArray sectionExposureTime,jbyteArray EXTRA_DATA) {
    uScanConfig *aScanConfig = new uScanConfig();

    aScanConfig->scanCfg.scan_type = scanType;
    aScanConfig->scanCfg.scanConfigIndex = scanConfigIndex;
    aScanConfig->scanCfg.num_repeats = numRepeat;
    aScanConfig->slewScanCfg.head.num_repeats = numRepeat;
    //SerialNumber-------------------------------------------------------------------------------------------------------------
    int scanConfigSerialNumber_len = env->GetArrayLength (scanConfigSerialNumber);
    char* scanConfigSerialNumberCharArray = new  char[scanConfigSerialNumber_len];
    env->GetByteArrayRegion (scanConfigSerialNumber, 0, scanConfigSerialNumber_len, reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray));
    for(int i=0;i<8;i++)
    {
        aScanConfig->scanCfg.ScanConfig_serial_number[i]=scanConfigSerialNumberCharArray[i];
    }
    env->ReleaseByteArrayElements(scanConfigSerialNumber,reinterpret_cast<jbyte*>(scanConfigSerialNumberCharArray),0);
    //configName--------------------------------------------------------------------------------------
    int configName_len = env->GetArrayLength (configName);
    char* configNameCharArray = new  char[configName_len];
    env->GetByteArrayRegion (configName, 0, configName_len, reinterpret_cast<jbyte*>(configNameCharArray));
    for(int i=0;i<40;i++)
    {
        aScanConfig->scanCfg.config_name[i] =configNameCharArray[i];
    }
    env->ReleaseByteArrayElements(configName,reinterpret_cast<jbyte*>(configNameCharArray),0);
    //----------------------------------------------------------------------------------------
    aScanConfig->slewScanCfg.head.num_sections = reinterpret_cast<jbyte>(numSections);
    //sectionScanType--------------------------------------------------------------------------------------------------------------
    int sectionScanType_len = env->GetArrayLength (sectionScanType);
    char* sectionScanTypeCharArray = new  char[sectionScanType_len];
    env->GetByteArrayRegion (sectionScanType, 0, sectionScanType_len, reinterpret_cast<jbyte*>(sectionScanTypeCharArray));
    int section = aScanConfig->slewScanCfg.head.num_sections;
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].section_scan_type =  sectionScanTypeCharArray[i];
    }
    env->ReleaseByteArrayElements(sectionScanType,reinterpret_cast<jbyte*>(sectionScanTypeCharArray),0);
    //sectionWidthPx--------------------------------------------------------------------------------------------------------------
    int sectionWidthPx_len = env->GetArrayLength (sectionWidthPx);
    char* sectionWidthPxCharArray = new  char[sectionWidthPx_len];
    env->GetByteArrayRegion (sectionWidthPx, 0, sectionWidthPx_len, reinterpret_cast<jbyte*>(sectionWidthPxCharArray));
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].width_px =  sectionWidthPxCharArray[i];
    }
    env->ReleaseByteArrayElements(sectionWidthPx,reinterpret_cast<jbyte*>(sectionWidthPxCharArray),0);
    //sectionWavelengthStartNm--------------------------------------------------------------------------------------------------------------
    int sectionWavelengthStartNm_len = env->GetArrayLength (sectionWavelengthStartNm);
    int * sectionWavelengthStartNmIntArray = new  int[sectionWidthPx_len];
    env->GetIntArrayRegion (sectionWavelengthStartNm, 0, sectionWavelengthStartNm_len, sectionWavelengthStartNmIntArray);
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].wavelength_start_nm= sectionWavelengthStartNmIntArray[i];
    }
    env->ReleaseIntArrayElements(sectionWavelengthStartNm,sectionWavelengthStartNmIntArray,0);
    //sectionWavelengthEndNm--------------------------------------------------------------------------------------------------------------
    int sectionWavelengthEndNm_len = env->GetArrayLength (sectionWavelengthEndNm);
    int * sectionWavelengthEndNmIntArray = new  int[sectionWavelengthEndNm_len];
    env->GetIntArrayRegion (sectionWavelengthEndNm, 0, sectionWavelengthEndNm_len, sectionWavelengthEndNmIntArray);
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].wavelength_end_nm= sectionWavelengthEndNmIntArray[i];
    }
    env->ReleaseIntArrayElements(sectionWavelengthEndNm,sectionWavelengthEndNmIntArray,0);
    //sectionNumPatterns--------------------------------------------------------------------------------------------------------------
    int sectionNumPatterns_len = env->GetArrayLength (sectionNumPatterns);
    int * sectionNumPatternsIntArray = new  int[sectionNumPatterns_len];
    env->GetIntArrayRegion (sectionNumPatterns, 0, sectionNumPatterns_len, sectionNumPatternsIntArray);
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].num_patterns=sectionNumPatternsIntArray[i];
    }
    env->ReleaseIntArrayElements(sectionNumPatterns,sectionNumPatternsIntArray,0);
    //sectionExposureTime--------------------------------------------------------------------------------------------------------------
    int sectionExposureTime_len = env->GetArrayLength (sectionExposureTime);
    int * sectionExposureTimeIntArray = new  int[sectionExposureTime_len];
    env->GetIntArrayRegion (sectionExposureTime, 0, sectionExposureTime_len, sectionExposureTimeIntArray);
    for(int i=0;i<section;i++)
    {
        aScanConfig->slewScanCfg.section[i].exposure_time = sectionExposureTimeIntArray[i];
    }
    env->ReleaseIntArrayElements(sectionExposureTime,sectionExposureTimeIntArray,0);

    char* pBuf = new  char[155];
    dlpspec_scan_write_configuration(aScanConfig, pBuf, 155);
    int EXTRA_DATA_len = env->GetArrayLength (EXTRA_DATA);
    char* EXTRA_DATACharArray = new  char[EXTRA_DATA_len];
    env->GetByteArrayRegion (EXTRA_DATA, 0, EXTRA_DATA_len, reinterpret_cast<jbyte*>(EXTRA_DATACharArray));
    for(int i=0;i<155;i++)
    {
        EXTRA_DATACharArray[i] = pBuf[i];
    }
    env->ReleaseByteArrayElements(EXTRA_DATA,reinterpret_cast<jbyte*>(EXTRA_DATACharArray),0);

    int ret = 1;
    return ret;
}

