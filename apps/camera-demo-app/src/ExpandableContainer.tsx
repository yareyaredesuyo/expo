import * as React from 'react';
import { ViewProps, LayoutChangeEvent, NativeMethodsMixinStatic } from 'react-native';
import Animated, { Easing } from 'react-native-reanimated';

interface Props extends ViewProps {
  expanded: boolean;
  duration: number;
}

interface State {
  height: Animated.Value<number>;
  contentHeight: number;
  animating: boolean;
  /**
   * Indicates whether [Props#expanded] is already respected
   */
  measured: boolean;
  measuring: boolean;
}

export default class ExpandableContainer extends React.PureComponent<Props, State> {
  readonly state: State = {
    height: new Animated.Value(0),
    contentHeight: 0,
    animating: false,
    measured: false,
    measuring: false,
  }

  static defaultProps = {
    duration: 300,
  }

  unmounted = false;
  contentHandle?: { getNode: () => NativeMethodsMixinStatic } | null;
  animation: Animated.BackwardCompatibleWrapper | undefined;

  componentDidUpdate(prevProps: Props) {
    if (prevProps.expanded !== this.props.expanded) {
      this.setState({ measured: false }, () => this.handleComponentUpdate(prevProps));
    } else {
      this.handleComponentUpdate(prevProps);
    }
  }

  componentWillUnmount() {
    this.unmounted = true;
  }

  handleComponentUpdate = (prevProps: Props) => {
    if (prevProps.expanded !== this.props.expanded) {
      this.toogleExpanded(this.props.expanded);
    }
  }

  toogleExpanded = (expanded: boolean) => {
    if (!expanded) {
      this.transitionToHeight(0);
    } else if (!this.contentHandle) {
      if (this.state.measured) {
        this.transitionToHeight(this.state.contentHeight);
      }
    } else {
      this.measureContent(this.transitionToHeight)
    }
  }

  transitionToHeight = (height: number) => {
    const { duration } = this.props;
    if (this.animation) {
      this.animation.stop();
    }

    this.setState({ animating: true });
    this.animation = Animated.timing(this.state.height, {
      toValue: height,
      duration,
      easing: Easing.inOut(Easing.ease),
    });
    this.animation.start(() => {
      if (this.unmounted) {
        return;
      }
      this.animation = undefined;
      this.setState({ animating: false });
    });
  }

  measureContent = (onMeasured: (height: number) => void) => {
    this.setState(
      { measuring: true},
      () => requestAnimationFrame(() => {
        if (!this.contentHandle) {
          this.setState(
            { measuring: false },
            () => onMeasured(0),
          );
        } else {
          this.contentHandle.getNode().measure(
            (_x, _y, _width, height) => this.setState(
              {
                measuring: false,
                measured: true,
                contentHeight: height,
              },
              () => onMeasured(height),
            ),
          );
        }
      }),
    );
  }

  handleLayoutChange = ({ nativeEvent: { layout: { height } } }: LayoutChangeEvent) => {
    const { animating, measuring, contentHeight } = this.state;
    const { expanded, } = this.props;
    if (animating || !expanded || measuring || height === contentHeight) {
      return;
    }

    this.state.height.setValue(height);
    this.setState({ contentHeight: height });
  }

  render() {
    const { expanded, children } = this.props;
    const { height, contentHeight, animating, measuring, measured } = this.state;

    const hasKnownHeight = !measuring && (measured || !expanded);
    const style = hasKnownHeight && {
      overflow: 'hidden' as const,
      height,
    };
    const contentStyle = {
      position: measuring ? 'absolute' as const : undefined,
      opacity: measuring ? 0 : undefined,
      translateY: measuring ? undefined : height.interpolate({
        inputRange: [0, contentHeight],
        outputRange: [-contentHeight, 0],
      }),
    };

    return (
      <Animated.View
        style={style}
      >
        <Animated.View
          ref={r => (this.contentHandle = r as any)}
          style={contentStyle}
          onLayout={animating ? undefined : this.handleLayoutChange}
        >
          {children}
        </Animated.View>
      </Animated.View>
    );
  }

}
