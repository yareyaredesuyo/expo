// Copyright 2019-present 650 Industries. All rights reserved.

#import <EXScreenOrientation/EXScreenOrientationModule.h>
#import <UMCore/UMEventEmitterService.h>
#import <EXScreenOrientation/EXScreenOrientationRegistry.h>
#import <UMCore/UMModuleRegistryProvider.h>

#import <UIKit/UIKit.h>
#import <sys/utsname.h>

@interface EXScreenOrientationModule ()

@property (nonatomic, weak) UMModuleRegistry *moduleRegistry;
@property (nonatomic, weak) id <UMEventEmitterService> eventEmitter;

@end

static int INVALID_MASK = 0;
static NSString *defaultAppId = @"bareAppId";

@implementation EXScreenOrientationModule{
  bool hasListeners;
}

UM_EXPORT_MODULE(ExpoScreenOrientation);

- (void)setModuleRegistry:(UMModuleRegistry *)moduleRegistry
{
  _moduleRegistry = moduleRegistry;
  _eventEmitter = [moduleRegistry getModuleImplementingProtocol:@protocol(UMEventEmitterService)];
  hasListeners = NO;
}

UM_EXPORT_METHOD_AS(lockAsync,
                    lockAsync:(NSString *)orientationLockString
                    resolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  EXOrientationLock orientationLock = [self stringToOrientationLock:orientationLockString];
  UIInterfaceOrientationMask orientationMask = [self orientationLockJSToNative:orientationLock];
  if (orientationMask == INVALID_MASK) {
    return reject(@"ERR_SCREEN_ORIENTATION_INVALID_ORIENTATION_LOCK", [NSString stringWithFormat:@"Invalid screen orientation lock %@", [self orientationLockToString:orientationLock]], nil);
  }
  if (![self doesSupportOrientationMask:orientationMask]) {
    return reject(@"ERR_SCREEN_ORIENTATION_UNSUPPORTED_ORIENTATION_LOCK", [NSString stringWithFormat:@"This device does not support this orientation %@", [self orientationLockToString:orientationLock]], nil);
  }
  [self setOrientationMask:orientationMask];
  [self enforceDesiredDeviceOrientationWithOrientationMask:orientationMask];
  resolve(nil);
}


UM_EXPORT_METHOD_AS(lockPlatformAsync,
                    lockPlatformAsync:(NSArray <NSString *> *)allowedOrientations
                    resolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  // combine all the allowedOrientations into one bitmask
  UIInterfaceOrientationMask allowedOrientationsMask = 0;
  for (NSString *allowedOrientation in allowedOrientations) {
    UIInterfaceOrientationMask orientationMask = [self orientationJSToNative: [self stringToOrientation:allowedOrientation]];
    allowedOrientationsMask = allowedOrientationsMask | orientationMask;
  }
  [self setOrientationMask:allowedOrientationsMask];
  [self enforceDesiredDeviceOrientationWithOrientationMask:allowedOrientationsMask];
  resolve(nil);
}

UM_EXPORT_METHOD_AS(unlockAsync,
                 unlockAsyncWithResolver:(UMPromiseResolveBlock)resolve
                                rejecter:(UMPromiseRejectBlock)reject)
{
  [self lockAsync:[self orientationLockToString:EXOrientationDefaultLock] resolver:resolve rejecter:reject];
}

UM_EXPORT_METHOD_AS(getOrientationLockAsync,
                    getOrientationLockAsyncWithResolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  UIInterfaceOrientationMask orientationMask = [self getOrientationMask];
  EXOrientationLock orientationLock = [self orientationLockNativeToJS:orientationMask];
  resolve([self orientationLockToString:orientationLock]);
}

UM_EXPORT_METHOD_AS(getPlatformOrientationLockAsync,
                    getPlatformOrientationLockAsyncResolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
    UIInterfaceOrientationMask orientationMask = [self getOrientationMask];
    NSArray *singleOrientations = @[@(UIInterfaceOrientationMaskPortrait),
                                    @(UIInterfaceOrientationMaskPortraitUpsideDown),
                                    @(UIInterfaceOrientationMaskLandscapeLeft),
                                    @(UIInterfaceOrientationMaskLandscapeRight)];
    // If the particular orientation is supported, we add it to the array of allowedOrientations
    NSMutableArray *allowedOrientations = [[NSMutableArray alloc] init];
    for (NSNumber *wrappedSingleOrientation in singleOrientations) {
      UIInterfaceOrientationMask singleOrientationMask = [wrappedSingleOrientation intValue];
      UIInterfaceOrientationMask supportedOrientation = orientationMask & singleOrientationMask;
      if (supportedOrientation == singleOrientationMask){
        EXOrientation orientation = [self orientationNativeToJS: (UIInterfaceOrientationMask) singleOrientationMask];
        [allowedOrientations addObject:[self orientationToString:orientation]];
      }
    }
    resolve([allowedOrientations copy]);
}

UM_EXPORT_METHOD_AS(doesSupportAsync,
                    doesSupportAsync:(NSString *)orientationLockString
                    resolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  [self supportsOrientationLockAsync:orientationLockString resolver:resolve rejecter:reject];
}

UM_EXPORT_METHOD_AS(supportsOrientationLockAsync,
                    supportsOrientationLockAsync:(NSString *)orientationLockString
                    resolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  EXOrientationLock orientationLock = [self stringToOrientationLock:orientationLockString];
  UIInterfaceOrientationMask orientationMask = [self orientationLockJSToNative:orientationLock];
  if (orientationMask == INVALID_MASK) {
    resolve(@NO);
  } else if ([self doesSupportOrientationMask:orientationMask]) {
    resolve(@YES);
  } else {
    resolve(@NO);
  }
}

UM_EXPORT_METHOD_AS(getOrientationAsync,
                    getOrientationAsyncResolver:(UMPromiseResolveBlock)resolve
                    rejecter:(UMPromiseRejectBlock)reject)
{
  resolve([self UIDeviceOrientationToUIINterfaceOrientation:[UIDevice currentDevice].orientation]);
}

+ (NSDictionary *)getStringToOrientationJSDict
{
  static NSDictionary*strToOrientationDict = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    strToOrientationDict = @{@"PORTRAIT" : @(EXOrientationPortrait),
                             @"PORTRAIT_UP" : @(EXOrientationPortraitUp),
                             @"PORTRAIT_DOWN" : @(EXOrientationPortraitDown),
                             @"LANDSCAPE" : @(EXOrientationLandscape),
                             @"LANDSCAPE_LEFT" : @(EXOrientationLandscapeLeft),
                             @"LANDSCAPE_RIGHT" : @(EXOrientationLandscapeRight),
                             @"UNKNOWN": @(EXOrientationUnknown)
                             };
  });
  return strToOrientationDict;
}

+ (NSDictionary *)getOrientationJSToStringDict
{
  static NSMutableDictionary*orientationToStrDict = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    orientationToStrDict = [[NSMutableDictionary alloc] init];
    NSDictionary *strToOrientation = [EXScreenOrientationModule getStringToOrientationJSDict];
    for(NSString *str in strToOrientation) {
      NSNumber *wrappedOrientation = [strToOrientation objectForKey:str];
      orientationToStrDict[wrappedOrientation] = str;
    }
  });
  return orientationToStrDict;
}

+ (NSDictionary *)getStringToOrientationLockJSDict
{
  static NSDictionary*strToOrientationLockDict = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    strToOrientationLockDict = @{ @"DEFAULT" : @(EXOrientationDefaultLock),
                                  @"ALL" : @(EXOrientationAllLock),
                                  @"PORTRAIT" : @(EXOrientationPortraitLock),
                                  @"PORTRAIT_UP" : @(EXOrientationPortraitUpLock),
                                  @"PORTRAIT_DOWN" : @(EXOrientationPortraitDownLock),
                                  @"LANDSCAPE" : @(EXOrientationLandscapeLock),
                                  @"LANDSCAPE_LEFT" : @(EXOrientationLandscapeLeftLock),
                                  @"LANDSCAPE_RIGHT" : @(EXOrientationLandscapeRightLock),
                                  @"OTHER" : @(EXOrientationOtherLock),
                                  @"ALL_BUT_UPSIDE_DOWN": @(EXOrientationAllButUpsideDownLock)
                                  };
  });
  return strToOrientationLockDict;
}

+ (NSDictionary *)getOrientationLockJSToStringDict
{
  static NSMutableDictionary*orientationLockToStrDict = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    orientationLockToStrDict = [[NSMutableDictionary alloc] init];
    NSDictionary *strToOrientationLock = [EXScreenOrientationModule getStringToOrientationLockJSDict];
    for(NSString *str in strToOrientationLock) {
      NSNumber *wrappedOrientationLock = [strToOrientationLock objectForKey:str];
      orientationLockToStrDict[wrappedOrientationLock] = str;
    }
  });
  return orientationLockToStrDict;
}

-(UIInterfaceOrientationMask)getOrientationMask{
  return [[self getRegistry] getOrientationMaskForAppId:defaultAppId];
}

- (void)setOrientationMask:(UIInterfaceOrientationMask)mask
{
  return [[self getRegistry] setOrientationMask:mask forAppId:defaultAppId];
}

-(EXScreenOrientationRegistry *)getRegistry
{
  return (EXScreenOrientationRegistry *)[UMModuleRegistryProvider getSingletonModuleForClass:[EXScreenOrientationRegistry class]];
}

// Will be called when this module's first listener is added.
- (void)startObserving
{
  if(!hasListeners){
    hasListeners = YES;
    [[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
    [[NSNotificationCenter defaultCenter]
     addObserver:self selector:@selector(handleScreenOrientationChange:)
     name:UIDeviceOrientationDidChangeNotification
     object:[UIDevice currentDevice]];
  }
}

// Will be called when this module's last listener is removed, or on dealloc.
- (void)stopObserving
{
//  hasListeners = NO;
//  if ([[UIDevice currentDevice] isGeneratingDeviceOrientationNotifications]){
//    [[UIDevice currentDevice] endGeneratingDeviceOrientationNotifications];
//  }
}

- (void)handleScreenOrientationChange:(NSNotification *)note
{
  if (hasListeners) {
    UIDevice *device = note.object;
    EXOrientationLock orientationLock = [self orientationLockNativeToJS:[self getOrientationMask]];
    [_eventEmitter sendEventWithName:@"expoDidUpdateDimensions" body:@{
                                                              @"orientation": [self UIDeviceOrientationToUIINterfaceOrientation:device.orientation],
                                                              @"orientationLock": [self orientationLockToString:orientationLock]
                                                              }];
  }
}

- (NSArray<NSString *> *)supportedEvents {
  return @[@"expoDidUpdateDimensions", @"expoDidUpdatePhysicalDimensions"];
}

- (BOOL)doesSupportOrientationMask:(UIInterfaceOrientationMask)orientationMask
{
  if ((UIInterfaceOrientationMaskPortraitUpsideDown & orientationMask) // UIInterfaceOrientationMaskPortraitUpsideDown is part of orientationMask
      && ![self doesDeviceSupportOrientationPortraitUpsideDown])
  {
    // device does not support UIInterfaceOrientationMaskPortraitUpsideDown and it was requested via orientationMask
    return FALSE;
  }
  
  return TRUE;
}

- (BOOL)doesDeviceSupportOrientationPortraitUpsideDown
{
  struct utsname systemInfo;
  uname(&systemInfo);
  NSString *deviceIdentifier = [NSString stringWithCString:systemInfo.machine
                                                  encoding:NSUTF8StringEncoding];
  return ![self doesDeviceHaveNotch:deviceIdentifier];
}
- (BOOL)doesDeviceHaveNotch:(NSString *)deviceIdentifier
{
  NSArray<NSString *> *devicesWithNotchIdentifiers = @[
                                                       @"iPhone10,3", // iPhoneX
                                                       @"iPhone10,6", // iPhoneX
                                                       @"iPhone11,2", // iPhoneXs
                                                       @"iPhone11,6", // iPhoneXsMax
                                                       @"iPhone11,4", // iPhoneXsMax
                                                       @"iPhone11,8", // iPhoneXr
                                                       ];
  NSArray<NSString *> *simulatorsIdentifiers = @[
                                                 @"i386",
                                                 @"x86_64",
                                                 ];
  
  if ([devicesWithNotchIdentifiers containsObject:deviceIdentifier]) {
    return YES;
  }
  
  if ([simulatorsIdentifiers containsObject:deviceIdentifier]) {
    return [self doesDeviceHaveNotch:[[[NSProcessInfo processInfo] environment] objectForKey:@"SIMULATOR_MODEL_IDENTIFIER"]];
  }
  return NO;
}

- (NSString *)stringFromSizeClass:(UIUserInterfaceSizeClass)sizeClass
{
  if (sizeClass == UIUserInterfaceSizeClassCompact){
    return @"COMPACT";
  } else if (sizeClass == UIUserInterfaceSizeClassRegular){
    return @"REGULAR";
  } else {
    return @"UNSPECIFIED";
  }
}

- (EXOrientation)orientationNativeToJS:(UIInterfaceOrientationMask)orientationMask
{
  if (orientationMask == (UIInterfaceOrientationMaskPortrait | UIInterfaceOrientationMaskPortraitUpsideDown)) {
    return EXOrientationPortrait;
  } else if (orientationMask == UIInterfaceOrientationMaskPortrait) {
    return EXOrientationPortraitUp;
  } else if (orientationMask == UIInterfaceOrientationMaskPortraitUpsideDown) {
    return EXOrientationPortraitDown;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscape) {
    return EXOrientationLandscape;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscapeLeft) {
    return EXOrientationLandscapeLeft;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscapeRight) {
    return EXOrientationLandscapeRight;
  } else {
    return EXOrientationUnknown;
  }
}

- (EXOrientationLock)orientationLockNativeToJS:(UIInterfaceOrientationMask)orientationMask
{
  if (orientationMask == UIInterfaceOrientationMaskAllButUpsideDown){
    return EXOrientationDefaultLock;
  } else if (orientationMask == UIInterfaceOrientationMaskAll) {
    return EXOrientationAllLock;
  } else if (orientationMask == (UIInterfaceOrientationMaskPortrait | UIInterfaceOrientationMaskPortraitUpsideDown)) {
    return EXOrientationPortraitLock;
  } else if (orientationMask == UIInterfaceOrientationMaskPortrait) {
    return EXOrientationPortraitUpLock;
  } else if (orientationMask == UIInterfaceOrientationMaskPortraitUpsideDown) {
    return EXOrientationPortraitDownLock;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscape) {
    return EXOrientationLandscapeLock;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscapeLeft) {
    return EXOrientationLandscapeLeftLock;
  } else if (orientationMask == UIInterfaceOrientationMaskLandscapeRight) {
    return EXOrientationLandscapeRightLock;
  } else {
    return EXOrientationOtherLock;
  }
}

- (UIInterfaceOrientationMask)orientationJSToNative:(EXOrientation)orientation
{
  if (orientation == EXOrientationPortrait) {
    return UIInterfaceOrientationMaskPortrait | UIInterfaceOrientationMaskPortraitUpsideDown;
  } else if (orientation == EXOrientationPortraitUp) {
    return UIInterfaceOrientationMaskPortrait;
  } else if (orientation == EXOrientationPortraitDown) {
    return UIInterfaceOrientationMaskPortraitUpsideDown;
  } else if (orientation == EXOrientationLandscape) {
    return UIInterfaceOrientationMaskLandscape;
  } else if (orientation == EXOrientationLandscapeLeft) {
    return UIInterfaceOrientationMaskLandscapeLeft;
  } else if (orientation == EXOrientationLandscapeRight) {
    return UIInterfaceOrientationMaskLandscapeRight;
  } else {
    return INVALID_MASK;
  }
}

- (UIInterfaceOrientationMask)orientationLockJSToNative:(EXOrientationLock)orientationLock
{
  if (orientationLock == EXOrientationDefaultLock) {
    return UIInterfaceOrientationMaskAllButUpsideDown;
  } else if (orientationLock == EXOrientationAllLock) {
    return UIInterfaceOrientationMaskAll;
  } else if (orientationLock == EXOrientationPortraitLock) {
    return UIInterfaceOrientationMaskPortrait | UIInterfaceOrientationMaskPortraitUpsideDown;
  } else if (orientationLock == EXOrientationPortraitUpLock) {
    return UIInterfaceOrientationMaskPortrait;
  } else if (orientationLock == EXOrientationPortraitDownLock) {
    return UIInterfaceOrientationMaskPortraitUpsideDown;
  } else if (orientationLock == EXOrientationLandscapeLock) {
    return UIInterfaceOrientationMaskLandscape;
  } else if (orientationLock == EXOrientationLandscapeLeftLock) {
    return UIInterfaceOrientationMaskLandscapeLeft;
  } else if (orientationLock == EXOrientationLandscapeRightLock) {
    return UIInterfaceOrientationMaskLandscapeRight;
  } else if (orientationLock == EXOrientationAllButUpsideDownLock) { // legacy
    return UIInterfaceOrientationMaskAllButUpsideDown;
  }else {
    return INVALID_MASK;
  }
}

- (EXOrientation)stringToOrientation:(NSString *)orientationString
{
  return [[[EXScreenOrientationModule getStringToOrientationJSDict] objectForKey:orientationString] intValue];
}

- (NSString *)orientationToString:(EXOrientation)orientation
{
  return [[EXScreenOrientationModule getOrientationJSToStringDict] objectForKey:@(orientation)];
}


- (EXOrientationLock)stringToOrientationLock:(NSString *)orientationLockString
{
  return [[[EXScreenOrientationModule getStringToOrientationLockJSDict] objectForKey:orientationLockString] intValue];
}

- (NSString *)orientationLockToString:(EXOrientationLock)orientationLock
{
  return [[EXScreenOrientationModule getOrientationLockJSToStringDict] objectForKey:@(orientationLock)];
}

// UIDevice and UIInterface landscape orientations are switched https://developer.apple.com/documentation/uikit/uiinterfaceorientation/uiinterfaceorientationlandscaperight?language=objc
- (NSString *)UIDeviceOrientationToUIINterfaceOrientation:(UIDeviceOrientation)deviceOrientation
{
    switch (deviceOrientation) {
      case UIDeviceOrientationPortrait:
        return [self orientationToString:EXOrientationPortraitUp];
      case UIDeviceOrientationPortraitUpsideDown:
        return [self orientationToString:EXOrientationPortraitDown];
      case UIDeviceOrientationLandscapeLeft:
        return [self orientationToString:EXOrientationLandscapeRight];
      case UIDeviceOrientationLandscapeRight:
        return [self orientationToString:EXOrientationLandscapeLeft];
      default:
        return [self orientationToString:EXOrientationUnknown];
    }
}

- (void)enforceDesiredDeviceOrientationWithOrientationMask:(UIInterfaceOrientationMask)orientationMask
{
  dispatch_async(dispatch_get_main_queue(), ^{
    UIDeviceOrientation currentOrientation = [[UIDevice currentDevice] orientation];
    UIInterfaceOrientation newOrientation = UIInterfaceOrientationUnknown;
    switch (orientationMask) {
      case (UIInterfaceOrientationMaskPortraitUpsideDown | UIInterfaceOrientationMaskPortrait):
        if (!UIDeviceOrientationIsPortrait(currentOrientation)) {
          newOrientation = UIInterfaceOrientationPortrait;
        }
        break;
      case UIInterfaceOrientationMaskPortrait:
        newOrientation = UIInterfaceOrientationPortrait;
        break;
      case UIInterfaceOrientationMaskPortraitUpsideDown:
        newOrientation = UIInterfaceOrientationPortraitUpsideDown;
        break;
      case UIInterfaceOrientationMaskLandscape:
        if (!UIDeviceOrientationIsLandscape(currentOrientation)) {
          newOrientation = UIInterfaceOrientationLandscapeLeft;
        }
        break;
      case UIInterfaceOrientationMaskLandscapeLeft:
        newOrientation = UIInterfaceOrientationLandscapeLeft;
        break;
      case UIInterfaceOrientationMaskLandscapeRight:
        newOrientation = UIInterfaceOrientationLandscapeRight;
        break;
      case UIInterfaceOrientationMaskAllButUpsideDown:
        if (currentOrientation == UIDeviceOrientationPortraitUpsideDown) {
          newOrientation = UIInterfaceOrientationPortrait;
        }
        break;
      default:
        break;
    }
    if (newOrientation != UIInterfaceOrientationUnknown) {
      [[UIDevice currentDevice] setValue:@(newOrientation) forKey:@"orientation"];
    }
    [UIViewController attemptRotationToDeviceOrientation];
  });
}

+ (UIInterfaceOrientationMask)getSupportedInterfaceOrientationsWithDefault:(UIInterfaceOrientationMask)defaultMask
{
  EXScreenOrientationRegistry *registry = (EXScreenOrientationRegistry *)[UMModuleRegistryProvider getSingletonModuleForClass:[EXScreenOrientationRegistry class]];
  if([registry doesKeyExistForAppId:defaultAppId])
  {
    return [registry getOrientationMaskForAppId:defaultAppId];
  }
  [registry setOrientationMask:defaultMask forAppId:defaultAppId];
  return defaultMask;
}

@end
